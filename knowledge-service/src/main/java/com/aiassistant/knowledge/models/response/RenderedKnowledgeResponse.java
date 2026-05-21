package com.aiassistant.knowledge.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RenderedKnowledgeResponse {
    String businessId;
    String text;
    Integer completenessScore;
}
