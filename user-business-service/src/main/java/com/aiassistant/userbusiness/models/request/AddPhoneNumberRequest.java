package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddPhoneNumberRequest {

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number too long")
    private String phoneNumber;

    @Size(max = 100)
    private String label;
}
