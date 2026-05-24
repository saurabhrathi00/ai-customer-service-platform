package com.aiassistant.userbusiness.models.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBusinessProfileRequest {

    @Size(max = 200)
    private String name;

    private String category;
    private String description;
    private String location;
    private String operatingHours;

    /** Owner WhatsApp number for appointment notifications. E.164. */
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "whatsappNumber must be E.164")
    private String whatsappNumber;
}