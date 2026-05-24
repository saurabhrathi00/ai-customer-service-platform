package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeclineLeadRequest {
    /** Required for appointment leads — gets quoted verbatim back to the
     *  customer in the decline WhatsApp. */
    @NotBlank(message = "reason is required when declining an appointment")
    @Size(max = 500)
    private String reason;
}
