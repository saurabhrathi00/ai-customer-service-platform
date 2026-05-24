package com.aiassistant.userbusiness.models.request;

import com.aiassistant.userbusiness.enums.LeadType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Internal payload posted by {@code conversation-summary-service} after a
 * call's LLM summary detects a lead trigger. Idempotent by {@code callLogId}.
 *
 * <p>{@code leadType} is nullable: when summary-service only has an interest
 * rating with no explicit appointment/human-request signal, it leaves type
 * null and this service applies the per-business HIGH_INTEREST threshold to
 * decide whether to create a lead at all.</p>
 */
@Data
public class CreateLeadRequest {

    @NotBlank
    private String businessId;
    @NotBlank
    private String callLogId;
    /** Null means "let the threshold decide". */
    private LeadType leadType;

    private String customerPhone;
    private String customerName;
    private String callerLanguage;

    private String summary;
    private BigDecimal interestRating;

    // APPOINTMENT-only — null otherwise.
    private String service;
    private String preferredWindowRaw;
    private List<Map<String, Object>> structuredSlots;
    private Instant suggestedDatetime;
}
