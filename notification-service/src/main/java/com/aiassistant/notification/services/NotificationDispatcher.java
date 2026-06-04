package com.aiassistant.notification.services;

import com.aiassistant.notification.clients.UserBusinessLeadClient;
import com.aiassistant.notification.configuration.ServiceConfiguration;
import com.aiassistant.notification.models.dto.LeadDto;
import com.aiassistant.notification.whatsapp.WhatsAppClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Templating + WhatsApp send for a single lead. Knows which template fits
 * which (lead type × event), what parameters it expects, and which marker
 * to set on user-business-service after a successful send.
 *
 * <p>All "send + mark" pairs are wrapped in a single try/catch — if the WA
 * send throws, we DO NOT mark as notified, so the next scheduler tick will
 * retry. If the mark-as-notified call fails after a successful send, the
 * caller will see a duplicate next tick — accepted tradeoff for at-least-once.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final DateTimeFormatter SLOT_FORMAT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a")
                    .withZone(java.time.ZoneId.of("Asia/Kolkata"));

    private final WhatsAppClient whatsApp;
    private final UserBusinessLeadClient ubsClient;
    private final ServiceConfiguration config;

    /** Owner ping the moment a NEW lead lands. */
    public void notifyOwnerOfNewLead(LeadDto lead) {
        String to = lead.getOwnerWhatsappNumber();
        if (to == null || to.isBlank()) {
            log.warn("[notify] owner WA missing for businessId={} leadId={}; marking as notified to avoid loop",
                    lead.getBusinessId(), lead.getId());
            ubsClient.markOwnerNotified(lead.getId());
            return;
        }
        try {
            whatsApp.sendTemplate(
                    to,
                    config.getWhatsapp().getOwnerNewLeadTemplate(),
                    List.of(
                            nullSafe(lead.getCustomerName(), "Unknown"),
                            nullSafe(lead.getCustomerPhone(), "no phone"),
                            humanLeadType(lead.getLeadType()),
                            nullSafe(lead.getSummary(), "(no summary)")),
                    lead.getId());      // dashboard deep-link suffix
            ubsClient.markOwnerNotified(lead.getId());
        } catch (Exception ex) {
            log.error("[notify] owner-new-lead failed leadId={}: {}", lead.getId(), ex.getMessage());
        }
    }

    /** Reminder while the lead is still NEW. */
    public void sendReminderToOwner(LeadDto lead) {
        String to = lead.getOwnerWhatsappNumber();
        if (to == null || to.isBlank()) {
            log.warn("[notify] owner WA missing leadId={}; skipping reminder", lead.getId());
            ubsClient.markReminderSent(lead.getId());  // still bump counter so we don't spin
            return;
        }
        try {
            // Same template — Meta treats it as a fresh send.
            whatsApp.sendTemplate(
                    to,
                    config.getWhatsapp().getOwnerNewLeadTemplate(),
                    List.of(
                            nullSafe(lead.getCustomerName(), "Unknown"),
                            nullSafe(lead.getCustomerPhone(), "no phone"),
                            humanLeadType(lead.getLeadType()),
                            nullSafe(lead.getSummary(), "(no summary)")),
                    lead.getId());
            ubsClient.markReminderSent(lead.getId());
        } catch (Exception ex) {
            log.error("[notify] reminder failed leadId={}: {}", lead.getId(), ex.getMessage());
        }
    }

    /** Customer-facing message after the owner decided. IGNORED never reaches here. */
    public void notifyCustomerOfDecision(LeadDto lead) {
        String to = lead.getCustomerPhone();
        if (to == null || to.isBlank()) {
            log.warn("[notify] customer phone missing leadId={}; marking notified to avoid loop", lead.getId());
            ubsClient.markCustomerNotified(lead.getId());
            return;
        }
        String status = lead.getStatus();
        String leadType = lead.getLeadType();
        try {
            if ("APPROVED".equals(status) && "APPOINTMENT".equals(leadType)) {
                whatsApp.sendTemplate(
                        to,
                        config.getWhatsapp().getCustomerApptConfirmedTemplate(),
                        List.of(
                                nullSafe(lead.getCustomerName(), "there"),
                                lead.getConfirmedDatetime() == null
                                        ? "the requested time"
                                        : SLOT_FORMAT.format(lead.getConfirmedDatetime()),
                                nullSafe(lead.getBusinessName(), "the business")),
                        null);
            } else if ("APPROVED".equals(status)) {
                whatsApp.sendTemplate(
                        to,
                        config.getWhatsapp().getCustomerAgentWillConnectTemplate(),
                        List.of(
                                nullSafe(lead.getCustomerName(), "there"),
                                nullSafe(lead.getBusinessName(), "the business")),
                        null);
            } else if ("DECLINED".equals(status)) {
                whatsApp.sendTemplate(
                        to,
                        config.getWhatsapp().getCustomerApptDeclinedTemplate(),
                        List.of(
                                nullSafe(lead.getCustomerName(), "there"),
                                nullSafe(lead.getBusinessName(), "the business")),
                        null);
            } else {
                // Should not happen — repo query already filters to APPROVED / DECLINED.
                log.warn("[notify] unexpected status for customer notification leadId={} status={}",
                        lead.getId(), status);
                ubsClient.markCustomerNotified(lead.getId());
                return;
            }
            ubsClient.markCustomerNotified(lead.getId());
        } catch (Exception ex) {
            log.error("[notify] customer-decision failed leadId={}: {}", lead.getId(), ex.getMessage());
        }
    }

    private static String humanLeadType(String t) {
        if (t == null) return "lead";
        return switch (t) {
            case "APPOINTMENT" -> "appointment request";
            case "HIGH_INTEREST" -> "high-interest lead";
            case "HUMAN_REQUEST" -> "needs a human";
            default -> "lead";
        };
    }

    private static String nullSafe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
