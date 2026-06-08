package com.aiassistant.subscription.models.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RecordUsageRequest {
    @NotBlank private String businessId;
    @NotBlank private String callId;
    @NotNull @Min(0) private Integer callDurationSecs;
}
