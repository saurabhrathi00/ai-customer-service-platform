package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.enums.ReminderMode;
import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.models.dao.LeadNotificationSettingsEntity;
import com.aiassistant.userbusiness.models.request.UpdateLeadNotificationSettingsRequest;
import com.aiassistant.userbusiness.models.response.LeadNotificationSettingsResponse;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.repository.LeadNotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Per-business reminder + threshold configuration. Created lazily so
 * existing businesses keep working without a backfill.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeadNotificationSettingsService {

    static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("7.0");
    static final ReminderMode DEFAULT_MODE = ReminderMode.FIXED;
    static final int DEFAULT_INTERVAL_MINUTES = 15;
    static final int DEFAULT_MAX_REMINDERS = 10;

    private final LeadNotificationSettingsRepository repository;
    private final BusinessRepository businessRepository;

    @Transactional
    public LeadNotificationSettingsEntity ensureForBusiness(String businessId) {
        return repository.findById(businessId).orElseGet(() -> {
            if (!businessRepository.existsById(businessId)) {
                throw new BusinessNotFoundException("Business not found: " + businessId);
            }
            return repository.save(LeadNotificationSettingsEntity.builder()
                    .businessId(businessId)
                    .highInterestThreshold(DEFAULT_THRESHOLD)
                    .reminderMode(DEFAULT_MODE)
                    .reminderIntervalMinutes(DEFAULT_INTERVAL_MINUTES)
                    .maxReminders(DEFAULT_MAX_REMINDERS)
                    .build());
        });
    }

    public LeadNotificationSettingsResponse get(String businessId) {
        return toResponse(ensureForBusiness(businessId));
    }

    @Transactional
    public LeadNotificationSettingsResponse update(
            String businessId, UpdateLeadNotificationSettingsRequest req) {
        LeadNotificationSettingsEntity e = ensureForBusiness(businessId);
        if (req.getHighInterestThreshold() != null) {
            e.setHighInterestThreshold(req.getHighInterestThreshold());
        }
        if (req.getReminderMode() != null) {
            e.setReminderMode(req.getReminderMode());
        }
        if (req.getReminderIntervalMinutes() != null) {
            e.setReminderIntervalMinutes(req.getReminderIntervalMinutes());
        }
        if (req.getMaxReminders() != null) {
            e.setMaxReminders(req.getMaxReminders());
        }
        return toResponse(e);
    }

    private static LeadNotificationSettingsResponse toResponse(LeadNotificationSettingsEntity e) {
        return LeadNotificationSettingsResponse.builder()
                .businessId(e.getBusinessId())
                .highInterestThreshold(e.getHighInterestThreshold())
                .reminderMode(e.getReminderMode() == null ? null : e.getReminderMode().name())
                .reminderIntervalMinutes(e.getReminderIntervalMinutes())
                .maxReminders(e.getMaxReminders())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
