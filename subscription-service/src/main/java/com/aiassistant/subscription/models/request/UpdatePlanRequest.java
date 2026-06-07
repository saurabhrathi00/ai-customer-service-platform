package com.aiassistant.subscription.models.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdatePlanRequest {
    @Size(max = 50)
    private String name;

    private String description;
    private Integer priceMonthly;
    private Integer callsIncluded;
    private Integer maxCallDurationSec;
    private Integer channels;
    private Integer phoneNumbers;
    private Integer extraCallRate;
    private Map<String, Object> features;
    private Boolean isActive;
    private Integer displayOrder;
    private Boolean isPopular;
}
