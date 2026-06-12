package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddNotificationRecipientRequest {

    @NotBlank(message = "whatsappNumber is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "whatsappNumber must be E.164 (e.g. +9198XXXXXXXX)")
    private String whatsappNumber;

    @Size(max = 100)
    private String label;
}
