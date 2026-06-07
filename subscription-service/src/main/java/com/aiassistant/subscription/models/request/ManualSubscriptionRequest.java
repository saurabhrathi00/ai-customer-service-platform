package com.aiassistant.subscription.models.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ManualSubscriptionRequest {
    @NotBlank
    private String businessId;

    @NotBlank
    private String planSlug;

    private String notes;
}
