package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateSubscriptionStatusRequest {
    @NotBlank
    private String subscriptionStatus;
    private String subscriptionId;
}
