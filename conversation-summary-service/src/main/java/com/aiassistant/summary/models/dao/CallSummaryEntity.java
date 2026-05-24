package com.aiassistant.summary.models.dao;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@Table(name = "call_summaries")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSummaryEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /** Foreign reference to call-orchestration-service's call_logs.id.
     *  No DB FK across schemas — enforced at the application level. */
    @Column(name = "call_log_id", nullable = false, unique = true)
    private String callLogId;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "caller_name")
    private String callerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "query_type")
    private String queryType;

    /** 0-10 with one decimal place (e.g. {@code 7.5}). Widened from the
     *  earlier 1-5 integer in {@code V2__widen_interest_rating.sql}. */
    @Column(name = "interest_rating")
    private java.math.BigDecimal interestRating;

    @Column(name = "interest_reason", columnDefinition = "TEXT")
    private String interestReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "main_concerns", columnDefinition = "jsonb")
    private List<String> mainConcerns;

    @Column(name = "callback_needed", nullable = false)
    private Boolean callbackNeeded;

    @Column(name = "callback_reason", columnDefinition = "TEXT")
    private String callbackReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unanswered_questions", columnDefinition = "jsonb")
    private List<String> unansweredQuestions;

    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "provider")
    private String provider;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = new ULID().nextULID();
        }
        if (callbackNeeded == null) {
            callbackNeeded = false;
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
