package com.aiassistant.knowledge.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FaqRequest {

    @NotBlank
    @Size(max = 500)
    private String question;

    @NotBlank
    @Size(max = 2000)
    private String answer;

    private Integer priority;
    private Boolean isActive;
}
