package com.aiassistant.userbusiness.models.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateRatingConfigRequest {

    @NotEmpty(message = "entries must not be empty")
    @Valid
    private List<Entry> entries;

    @Data
    public static class Entry {
        @NotBlank(message = "signalKey is required")
        private String signalKey;

        @NotNull(message = "scoreValue is required")
        private Integer scoreValue;
    }
}
