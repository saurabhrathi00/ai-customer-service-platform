package com.aiassistant.aiconversation.models.request;

import com.aiassistant.aiconversation.llm.LlmMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RespondRequest {
    @NotBlank private String businessId;
    @NotBlank private String systemPrompt;
    @NotEmpty private List<LlmMessage> messages;
    private String provider;
    private Integer maxOutputTokens;
    private Double temperature;
}
