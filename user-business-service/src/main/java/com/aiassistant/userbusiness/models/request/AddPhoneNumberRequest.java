package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddPhoneNumberRequest {

    @NotBlank(message = "twilioNumber is required")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "twilioNumber must be E.164 format (e.g. +14155551234)")
    private String twilioNumber;

    @Size(max = 100)
    private String label;
}
