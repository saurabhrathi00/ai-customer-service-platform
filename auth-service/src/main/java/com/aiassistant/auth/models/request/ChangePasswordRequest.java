package com.aiassistant.auth.models.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 128)
    private String newPassword;
}
