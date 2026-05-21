package com.aiassistant.knowledge.models.dao;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "business_profile")
public class BusinessProfileEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "business_id", nullable = false, unique = true)
    private String businessId;

    // JSONB columns stored as String — service layer (de)serialises with Jackson.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "jsonb")
    private String businessHoursJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "services_offered", columnDefinition = "jsonb")
    private String servicesOfferedJson;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "location_notes", columnDefinition = "TEXT")
    private String locationNotes;

    @Column(name = "alt_phone")
    private String altPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "website_url")
    private String websiteUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "languages_spoken", columnDefinition = "text[]")
    private List<String> languagesSpoken;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "payment_methods", columnDefinition = "text[]")
    private List<String> paymentMethods;

    @Column(name = "appointment_policy", columnDefinition = "TEXT")
    private String appointmentPolicy;

    @Column(name = "cancellation_policy", columnDefinition = "TEXT")
    private String cancellationPolicy;

    @Column(name = "refund_policy", columnDefinition = "TEXT")
    private String refundPolicy;

    @Column(name = "completeness_score", nullable = false)
    private Integer completenessScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = new ULID().nextULID();
        if (completenessScore == null) completenessScore = 0;
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
