package com.aiassistant.callorchestration.models.dao;

import de.huxhorn.sulky.ulid.ULID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "call_logs")
public class CallLogEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_call_id", nullable = false, unique = true)
    private String providerCallId;

    @Column(name = "query_type")
    private String queryType;

    @Column(name = "call_summary", columnDefinition = "TEXT")
    private String callSummary;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "call_duration_secs")
    private Integer callDurationSecs;

    @Column(name = "feedback_score")
    private Integer feedbackScore;

    @Column(name = "interest_rating")
    private Integer interestRating;

    @Column(name = "callback_requested", nullable = false)
    private Boolean callbackRequested;

    @Column(name = "call_started_at", nullable = false)
    private Instant callStartedAt;

    @Column(name = "call_ended_at")
    private Instant callEndedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (this.id == null) {
            this.id = new ULID().nextULID();
        }
        if (this.callbackRequested == null) {
            this.callbackRequested = false;
        }
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
