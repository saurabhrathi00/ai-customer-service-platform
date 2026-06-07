package com.aiassistant.subscription.models.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutRequest {
    @NotBlank
    private String planSlug;

    private String businessId;

    @NotBlank @Size(max = 200)
    private String businessName;

    @Size(max = 100)
    private String ownerName;

    @NotBlank @Email
    private String email;

    @NotBlank @Size(max = 15)
    private String phone;

    @Size(max = 50)
    private String industry;

    @Size(max = 20)
    private String gstNumber;
}
