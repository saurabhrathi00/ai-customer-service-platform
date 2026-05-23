package com.aiassistant.aiconversation.models.dao;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "conversation_histories")
public class ConversationHistoryEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "conversation_id", nullable = false, unique = true)
    private String conversationId;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "language")
    private String language;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount;

    /** List of {@code {"role", "content"}} entries — the canonical
     *  conversation as the LLM saw it. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "messages", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, String>> messages;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = new ULID().nextULID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
