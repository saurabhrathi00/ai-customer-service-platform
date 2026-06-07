package com.aiassistant.subscription.models.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreatePlanRequest {
    @NotBlank @Size(max = 50)
    private String name;

    @NotBlank @Size(max = 50)
    private String slug;

    private String description;

    @Positive
    private int priceMonthly;

    @Positive
    private int callsIncluded;

    @Positive
    private int maxCallDurationSec;

    @Positive
    private int channels;

    @Positive
    private int phoneNumbers;

    @PositiveOrZero
    private int extraCallRate;

    private Map<String, Object> features;

    @PositiveOrZero
    private int displayOrder;

    private boolean isPopular;
}
