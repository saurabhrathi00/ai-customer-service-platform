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
@Table(name = "usage_events")
public class UsageEventEntity {

    @Id
    @Column(name = "id", length = 26)
    private String id;

    @Column(name = "business_id", nullable = false, length = 26)
    private String businessId;

    @Column(name = "subscription_id", length = 26)
    private String subscriptionId;

    @Column(name = "call_id", nullable = false, unique = true, length = 26)
    private String callId;

    @Column(name = "call_duration_secs", nullable = false)
    private int callDurationSecs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.id == null) this.id = new ULID().nextULID();
        this.createdAt = Instant.now();
    }
}
