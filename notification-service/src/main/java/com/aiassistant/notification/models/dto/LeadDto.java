package com.aiassistant.notification.models.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Notification-service's mirror of user-business-service's {@code LeadResponse}.
 * Only the fields the scheduler + WhatsApp templates need are carried here;
 * unknown JSON properties are ignored so adding a field on the UBS side
 * doesn't break this service.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeadDto {
    private String id;
    private String businessId;
    private String callLogId;
    private String leadType;        // APPOINTMENT | HIGH_INTEREST | HUMAN_REQUEST

    private String customerPhone;
    private String customerName;
    private String callerLanguage;

    private String summary;
    private BigDecimal interestRating;

    // APPOINTMENT-only fields used by templates.
    private String service;
    private String preferredWindowRaw;
    private Instant suggestedDatetime;
    private Instant confirmedDatetime;
    private String declineReason;

    private String status;          // NEW | APPROVED | DECLINED | IGNORED

    private Integer remindersSent;

    // Denormalised business context — server-side join.
    private String businessName;
    private String ownerWhatsappNumber;
    /** Additional active recipients; same template goes to each. May be null. */
    private List<String> additionalWhatsappNumbers;

    private Instant ownerNotifiedAt;
    private Instant customerNotifiedAt;

    private Instant createdAt;
}
