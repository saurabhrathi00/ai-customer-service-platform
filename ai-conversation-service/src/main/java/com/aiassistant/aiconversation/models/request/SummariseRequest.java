package com.aiassistant.aiconversation.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SummariseRequest {
    @NotBlank private String businessId;
    @NotEmpty private List<TranscriptLine> transcript;
    private String provider;
    private Integer maxOutputTokens;

    @Data
    public static class TranscriptLine {
        @NotBlank private String speaker;
        @NotBlank private String text;
    }
}
