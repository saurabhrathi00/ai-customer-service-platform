package com.aiassistant.auth.models.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SigninRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;
}
