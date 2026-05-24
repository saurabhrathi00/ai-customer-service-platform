package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.enums.LeadDecisionChannel;
import com.aiassistant.userbusiness.enums.LeadStatus;
import com.aiassistant.userbusiness.enums.LeadType;
import com.aiassistant.userbusiness.enums.ReminderMode;
import com.aiassistant.userbusiness.exceptions.AppException;
import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.models.dao.LeadEntity;
import com.aiassistant.userbusiness.models.dao.LeadNotificationSettingsEntity;
import com.aiassistant.userbusiness.models.request.CreateLeadRequest;
import com.aiassistant.userbusiness.models.response.LeadResponse;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Lead lifecycle service. State machine:
 * <pre>
 *   NEW ──approve──▶ APPROVED   (terminal; APPOINTMENT requires confirmedDatetime)
 *   NEW ──decline──▶ DECLINED   (terminal; APPOINTMENT only, reason required)
 *   NEW ──ignore───▶ IGNORED    (terminal; no customer notification)
 * </pre>
 * Any terminal transition clears {@code next_reminder_at} so the reminder
 * scheduler skips the row.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository repository;
    private final BusinessRepository businessRepository;
    private final LeadNotificationSettingsService settingsService;

    /**
     * Idempotent — repeat calls for the same callLogId return the existing
     * row. summary-service retries are expected.
     *
     * <p>When {@code req.leadType} is null, the per-business
     * {@code high_interest_threshold} is applied to {@code interestRating}:
     * the lead is created as HIGH_INTEREST if the rating meets the threshold,
     * otherwise the call returns {@link java.util.Optional#empty()} (the
     * controller maps that to 204 No Content). Explicit types skip the gate.
     * </p>
     */
    @Transactional
    public java.util.Optional<LeadResponse> createFromCall(CreateLeadRequest req) {
        if (!businessRepository.existsById(req.getBusinessId())) {
            throw new BusinessNotFoundException("Business not found: " + req.getBusinessId());
        }
        var existing = repository.findByCallLogId(req.getCallLogId());
        if (existing.isPresent()) {
            log.info("[lead] existing for callLogId={} — returning id={}",
                    req.getCallLogId(), existing.get().getId());
            return java.util.Optional.of(toResponse(existing.get()));
        }

        // Lazy-init settings so the scheduler can read them.
        LeadNotificationSettingsEntity settings =
                settingsService.ensureForBusiness(req.getBusinessId());

        LeadType resolvedType = req.getLeadType();
        if (resolvedType == null) {
            if (req.getInterestRating() == null
                    || req.getInterestRating().compareTo(settings.getHighInterestThreshold()) < 0) {
                log.info("[lead] drop callLogId={} reason=below-threshold interest={} threshold={}",
                        req.getCallLogId(), req.getInterestRating(),
                        settings.getHighInterestThreshold());
                return java.util.Optional.empty();
            }
            resolvedType = LeadType.HIGH_INTEREST;
        }

        LeadEntity entity = LeadEntity.builder()
                .businessId(req.getBusinessId())
                .callLogId(req.getCallLogId())
                .leadType(resolvedType)
                .customerPhone(req.getCustomerPhone())
                .customerName(req.getCustomerName())
                .callerLanguage(req.getCallerLanguage())
                .summary(req.getSummary())
                .interestRating(req.getInterestRating())
                .service(req.getService())
                .preferredWindowRaw(req.getPreferredWindowRaw())
                .structuredSlots(req.getStructuredSlots())
                .suggestedDatetime(req.getSuggestedDatetime())
                .status(LeadStatus.NEW)
                .remindersSent(0)
                .nextReminderAt(computeNextReminderAt(settings, 0, Instant.now()))
                .build();
        repository.save(entity);
        log.info("[lead] created id={} businessId={} type={} interest={}",
                entity.getId(), entity.getBusinessId(), entity.getLeadType(),
                entity.getInterestRating());
        return java.util.Optional.of(toResponse(entity));
    }

    public List<LeadResponse> listForBusiness(String businessId) {
        return repository.findByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(this::toResponse)
                .toList();
    }

    public LeadResponse get(String businessId, String leadId) {
        return toResponse(loadTenanted(businessId, leadId));
    }

    @Transactional
    public LeadResponse approve(String businessId, String leadId, Instant confirmedDatetime,
                                LeadDecisionChannel via) {
        LeadEntity e = loadPending(businessId, leadId);
        if (e.getLeadType() == LeadType.APPOINTMENT) {
            if (confirmedDatetime == null) {
                throw new AppException("confirmedDatetime is required to approve an appointment");
            }
            e.setConfirmedDatetime(confirmedDatetime);
        }
        return finalise(e, LeadStatus.APPROVED, via);
    }

    @Transactional
    public LeadResponse decline(String businessId, String leadId, String reason,
                                LeadDecisionChannel via) {
        LeadEntity e = loadPending(businessId, leadId);
        if (e.getLeadType() != LeadType.APPOINTMENT) {
            throw new AppException("Decline is only valid for APPOINTMENT leads");
        }
        if (reason == null || reason.isBlank()) {
            throw new AppException("reason is required when declining");
        }
        e.setDeclineReason(reason);
        return finalise(e, LeadStatus.DECLINED, via);
    }

    @Transactional
    public LeadResponse ignore(String businessId, String leadId, LeadDecisionChannel via) {
        LeadEntity e = loadPending(businessId, leadId);
        return finalise(e, LeadStatus.IGNORED, via);
    }

    // ---- Scheduler-side surface (called by notification-service) ----

    /** Returns leads whose reminder is due NOW across all businesses. */
    public List<LeadResponse> listDueReminders(Instant cutoff) {
        return repository
                .findByStatusAndNextReminderAtLessThanEqualOrderByNextReminderAtAsc(
                        LeadStatus.NEW, cutoff)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Leads we haven't notified the owner about yet. */
    public List<LeadResponse> listPendingOwnerNotifications() {
        return repository.findByOwnerNotifiedAtIsNullOrderByCreatedAtAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** Terminal leads (APPROVED/DECLINED) whose customer notification hasn't fired. */
    public List<LeadResponse> listPendingCustomerNotifications() {
        return repository.findPendingCustomerNotifications().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public LeadResponse recordOwnerNotified(String leadId) {
        LeadEntity e = repository.findById(leadId)
                .orElseThrow(() -> new BusinessNotFoundException("Lead not found"));
        if (e.getOwnerNotifiedAt() == null) e.setOwnerNotifiedAt(Instant.now());
        return toResponse(e);
    }

    @Transactional
    public LeadResponse recordCustomerNotified(String leadId) {
        LeadEntity e = repository.findById(leadId)
                .orElseThrow(() -> new BusinessNotFoundException("Lead not found"));
        if (e.getCustomerNotifiedAt() == null) e.setCustomerNotifiedAt(Instant.now());
        return toResponse(e);
    }

    @Transactional
    public LeadResponse recordReminderSent(String leadId) {
        LeadEntity e = repository.findById(leadId)
                .orElseThrow(() -> new BusinessNotFoundException("Lead not found"));
        // Race guard — terminal state may have happened between scheduler claim
        // and record. Don't bump the counter for a lead we no longer remind on.
        if (e.getStatus() != LeadStatus.NEW) {
            return toResponse(e);
        }
        LeadNotificationSettingsEntity settings =
                settingsService.ensureForBusiness(e.getBusinessId());
        int sent = (e.getRemindersSent() == null ? 0 : e.getRemindersSent()) + 1;
        Instant now = Instant.now();
        e.setRemindersSent(sent);
        e.setLastReminderAt(now);
        e.setNextReminderAt(
                sent >= settings.getMaxReminders()
                        ? null
                        : computeNextReminderAt(settings, sent, now));
        return toResponse(e);
    }

    // ---- helpers ----

    private LeadEntity loadPending(String businessId, String leadId) {
        LeadEntity e = loadTenanted(businessId, leadId);
        if (e.getStatus() != LeadStatus.NEW) {
            throw new AppException("Lead is not pending — already " + e.getStatus());
        }
        return e;
    }

    private LeadEntity loadTenanted(String businessId, String leadId) {
        return repository.findById(leadId)
                .filter(l -> l.getBusinessId().equals(businessId))
                .orElseThrow(() -> new BusinessNotFoundException("Lead not found"));
    }

    private LeadResponse finalise(LeadEntity e, LeadStatus terminal, LeadDecisionChannel via) {
        e.setStatus(terminal);
        e.setDecidedAt(Instant.now());
        e.setDecidedVia(via);
        // Terminal — scheduler must skip this row forever.
        e.setNextReminderAt(null);
        log.info("[lead] {} id={} via={}", terminal, e.getId(), via);
        return toResponse(e);
    }

    /**
     * Pick the next reminder fire-time given the schedule shape and how
     * many reminders have already gone out. FIXED is a constant offset;
     * INCREMENT scales linearly with the count, so reminder #N fires
     * {@code (N+1) * interval} after the lead was created.
     *
     * <p>Caller is responsible for nulling the result if {@code sentCount}
     * has already hit {@code max_reminders}.</p>
     */
    static Instant computeNextReminderAt(LeadNotificationSettingsEntity settings,
                                         int sentCount, Instant anchor) {
        long offsetMinutes = switch (settings.getReminderMode()) {
            case FIXED -> settings.getReminderIntervalMinutes();
            case INCREMENT -> (long) settings.getReminderIntervalMinutes() * (sentCount + 1);
        };
        return anchor.plus(offsetMinutes, ChronoUnit.MINUTES);
    }

    private LeadResponse toResponse(LeadEntity e) {
        // N+1 fetch of business context. Acceptable at MVP scale (leads
        // counts are small); switch to a join projection if it bites.
        var business = businessRepository.findById(e.getBusinessId()).orElse(null);
        return LeadResponse.builder()
                .businessName(business == null ? null : business.getName())
                .ownerWhatsappNumber(business == null ? null : business.getWhatsappNumber())
                .ownerNotifiedAt(e.getOwnerNotifiedAt())
                .customerNotifiedAt(e.getCustomerNotifiedAt())
                .id(e.getId())
                .businessId(e.getBusinessId())
                .callLogId(e.getCallLogId())
                .leadType(e.getLeadType() == null ? null : e.getLeadType().name())
                .customerPhone(e.getCustomerPhone())
                .customerName(e.getCustomerName())
                .callerLanguage(e.getCallerLanguage())
                .summary(e.getSummary())
                .interestRating(e.getInterestRating())
                .service(e.getService())
                .preferredWindowRaw(e.getPreferredWindowRaw())
                .structuredSlots(e.getStructuredSlots())
                .suggestedDatetime(e.getSuggestedDatetime())
                .status(e.getStatus() == null ? null : e.getStatus().name())
                .confirmedDatetime(e.getConfirmedDatetime())
                .declineReason(e.getDeclineReason())
                .decidedAt(e.getDecidedAt())
                .decidedVia(e.getDecidedVia() == null ? null : e.getDecidedVia().name())
                .remindersSent(e.getRemindersSent())
                .lastReminderAt(e.getLastReminderAt())
                .nextReminderAt(e.getNextReminderAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
