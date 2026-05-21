package com.aiassistant.userbusiness.models.request;

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
}