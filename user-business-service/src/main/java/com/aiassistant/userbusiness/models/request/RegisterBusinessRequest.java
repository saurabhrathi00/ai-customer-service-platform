package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterBusinessRequest {

    @NotBlank(message = "name is required")
    @Size(max = 200)
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 100, message = "password must be 8-100 characters")
    private String password;

    private String category;
    private String description;
    private String location;
    private String operatingHours;
}