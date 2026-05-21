package com.aiassistant.callorchestration.services.mapper;

import com.aiassistant.callorchestration.models.dao.CallLogEntity;
import com.aiassistant.callorchestration.models.response.CallLogResponse;
import com.aiassistant.callorchestration.models.response.CallbackResponse;

public final class CallLogMapper {

    private CallLogMapper() {}

    public static CallLogResponse toResponse(CallLogEntity e) {
        return CallLogResponse.builder()
                .id(e.getId())
                .businessId(e.getBusinessId())
                .customerPhone(e.getCustomerPhone())
                .customerName(e.getCustomerName())
                .provider(e.getProvider())
                .providerCallId(e.getProviderCallId())
                .queryType(e.getQueryType())
                .callSummary(e.getCallSummary())
                .transcript(e.getTranscript())
                .callDurationSecs(e.getCallDurationSecs())
                .feedbackScore(e.getFeedbackScore())
                .interestRating(e.getInterestRating())
                .callbackRequested(e.getCallbackRequested())
                .callStartedAt(e.getCallStartedAt())
                .callEndedAt(e.getCallEndedAt())
                .build();
    }

    public static CallbackResponse toCallbackResponse(CallLogEntity e) {
        return CallbackResponse.builder()
                .callId(e.getId())
                .businessId(e.getBusinessId())
                .customerPhone(e.getCustomerPhone())
                .customerName(e.getCustomerName())
                .callSummary(e.getCallSummary())
                .callStartedAt(e.getCallStartedAt())
                .build();
    }
}
