package com.aiassistant.userbusiness.models.dao;

import com.aiassistant.userbusiness.enums.LeadDecisionChannel;
import com.aiassistant.userbusiness.enums.LeadStatus;
import com.aiassistant.userbusiness.enums.LeadType;
import de.huxhorn.sulky.ulid.ULID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Post-call lead handed off to the owner. One row per call (unique on
 * {@code call_log_id}) keeps the create endpoint idempotent — summary-service
 * retries are safe.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "leads")
public class LeadEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "call_log_id", nullable = false, unique = true)
    private String callLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_type", nullable = false)
    private LeadType leadType;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "caller_language")
    private String callerLanguage;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "interest_rating")
    private BigDecimal interestRating;

    // ----- APPOINTMENT-only fields. NULL for HIGH_INTEREST / HUMAN_REQUEST. -----
    @Column(name = "service")
    private String service;

    @Column(name = "preferred_window_raw", columnDefinition = "TEXT")
    private String preferredWindowRaw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_slots", columnDefinition = "jsonb")
    private List<Map<String, Object>> structuredSlots;

    /** LLM's single best-guess datetime. Used by the dashboard to pre-fill
     *  the approve form so the owner often just confirms without editing. */
    @Column(name = "suggested_datetime")
    private Instant suggestedDatetime;

    // ----- Decision -----
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LeadStatus status;

    @Column(name = "confirmed_datetime")
    private Instant confirmedDatetime;

    @Column(name = "decline_reason", columnDefinition = "TEXT")
    private String declineReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_via")
    private LeadDecisionChannel decidedVia;

    // ----- Reminder bookkeeping -----
    @Column(name = "reminders_sent", nullable = false)
    private Integer remindersSent;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @Column(name = "next_reminder_at")
    private Instant nextReminderAt;

    /** Stamped by notification-service after the initial owner WhatsApp
     *  fires. Null means "owner hasn't been told about this lead yet". */
    @Column(name = "owner_notified_at")
    private Instant ownerNotifiedAt;

    /** Stamped by notification-service after the customer-facing terminal
     *  WhatsApp fires (confirm / decline). Null means "customer hasn't been
     *  told about the decision yet". IGNORED leads never set this. */
    @Column(name = "customer_notified_at")
    private Instant customerNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = new ULID().nextULID();
        if (status == null) status = LeadStatus.NEW;
        if (remindersSent == null) remindersSent = 0;
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
