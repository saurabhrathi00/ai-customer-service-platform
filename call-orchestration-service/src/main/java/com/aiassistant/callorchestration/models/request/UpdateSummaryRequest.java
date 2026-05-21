package com.aiassistant.callorchestration.models.request;

import lombok.Data;

@Data
public class UpdateSummaryRequest {
    private String businessId;
    private String callSummary;
    private String queryType;
    private Integer interestRating;
}
