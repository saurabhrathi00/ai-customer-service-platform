package com.aiassistant.subscription.models.dao;

import de.huxhorn.sulky.ulid.ULID;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_monthly", nullable = false)
    private int priceMonthly;

    @Column(name = "calls_included", nullable = false)
    private int callsIncluded;

    @Column(name = "max_call_duration_sec", nullable = false)
    private int maxCallDurationSec;

    @Column(name = "channels", nullable = false)
    private int channels;

    @Column(name = "phone_numbers", nullable = false)
    private int phoneNumbers;

    @Column(name = "extra_call_rate", nullable = false)
    private int extraCallRate;

    @Column(name = "features", columnDefinition = "jsonb", nullable = false)
    private String features;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_popular", nullable = false)
    private boolean isPopular;

    @Column(name = "razorpay_plan_id", length = 50)
    private String razorpayPlanId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = new ULID().nextULID();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
