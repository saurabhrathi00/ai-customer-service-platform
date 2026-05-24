package com.aiassistant.userbusiness.models.dao;

import com.aiassistant.userbusiness.enums.ReminderMode;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-business configuration for the lead handoff workflow. One row per
 * business, keyed by businessId. Created lazily on first read so existing
 * businesses transparently get the defaults.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "lead_notification_settings")
public class LeadNotificationSettingsEntity {

    @Id
    @Column(name = "business_id", updatable = false, nullable = false)
    private String businessId;

    /** Interest rating (on 0-10 scale) that triggers a HIGH_INTEREST lead.
     *  Range enforced in the service: 0.0 – 10.0. */
    @Column(name = "high_interest_threshold", nullable = false)
    private BigDecimal highInterestThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_mode", nullable = false)
    private ReminderMode reminderMode;

    /** Base interval (minutes) for the reminder schedule. FIXED mode uses
     *  this verbatim; INCREMENT multiplies by reminder count. */
    @Column(name = "reminder_interval_minutes", nullable = false)
    private Integer reminderIntervalMinutes;

    /** Hard cap so a forgotten lead doesn't ping forever. */
    @Column(name = "max_reminders", nullable = false)
    private Integer maxReminders;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
