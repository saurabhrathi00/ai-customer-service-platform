package com.aiassistant.knowledge.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EscalationRuleRequest {

    @NotBlank
    @Size(max = 500)
    private String triggerPhrase;

    @NotBlank
    @Pattern(regexp = "TRANSFER|CALLBACK|DECLINE",
            message = "action must be TRANSFER, CALLBACK, or DECLINE")
    private String action;

    @Size(max = 1000)
    private String actionMessage;

    private Boolean isActive;
}
