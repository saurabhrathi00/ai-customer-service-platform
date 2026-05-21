package com.aiassistant.knowledge.models.dao;

import de.huxhorn.sulky.ulid.ULID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
@Table(name = "business_escalation_rule")
public class BusinessEscalationRuleEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "trigger_phrase", nullable = false, columnDefinition = "TEXT")
    private String triggerPhrase;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "action_message", columnDefinition = "TEXT")
    private String actionMessage;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = new ULID().nextULID();
        if (isActive == null) isActive = true;
        createdAt = Instant.now();
    }
}
