package com.aiassistant.summary.services;

import com.aiassistant.summary.models.dao.CallSummaryEntity;
import com.aiassistant.summary.models.response.CallSummaryResponse;
import com.aiassistant.summary.repository.CallSummaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SummaryQueryService {

    private final CallSummaryRepository repository;

    public List<CallSummaryResponse> listByBusiness(String businessId) {
        return repository.findByBusinessIdOrderByCreatedAtDesc(businessId).stream()
                .map(SummaryQueryService::toResponse)
                .toList();
    }

    private static CallSummaryResponse toResponse(CallSummaryEntity e) {
        return CallSummaryResponse.builder()
                .id(e.getId())
                .callLogId(e.getCallLogId())
                .businessId(e.getBusinessId())
                .callerName(e.getCallerName())
                .customerPhone(e.getCustomerPhone())
                .queryType(e.getQueryType())
                .interestRating(e.getInterestRating())
                .interestReason(e.getInterestReason())
                .mainConcerns(e.getMainConcerns())
                .callbackNeeded(e.getCallbackNeeded())
                .callbackReason(e.getCallbackReason())
                .unansweredQuestions(e.getUnansweredQuestions())
                .summaryText(e.getSummaryText())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
