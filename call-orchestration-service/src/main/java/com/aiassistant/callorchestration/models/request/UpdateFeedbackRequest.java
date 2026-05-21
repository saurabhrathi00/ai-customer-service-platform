package com.aiassistant.callorchestration.models.request;

import lombok.Data;

@Data
public class UpdateFeedbackRequest {
    private String businessId;
    private Integer feedbackScore;
}
