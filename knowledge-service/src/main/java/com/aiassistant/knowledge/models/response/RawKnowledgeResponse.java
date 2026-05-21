package com.aiassistant.knowledge.models.response;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

@Value
@Builder
@Jacksonized
public class RawKnowledgeResponse {
    String businessId;
    ProfileResponse profile;
    FreeformResponse freeform;
    List<FaqResponse> faqs;
    List<EscalationRuleResponse> escalations;
}
