package com.aiassistant.knowledge.services.mapper;

import com.aiassistant.knowledge.exceptions.AppException;
import com.aiassistant.knowledge.models.dao.BusinessEscalationRuleEntity;
import com.aiassistant.knowledge.models.dao.BusinessFaqEntity;
import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import com.aiassistant.knowledge.models.response.EscalationRuleResponse;
import com.aiassistant.knowledge.models.response.FaqResponse;
import com.aiassistant.knowledge.models.response.FreeformResponse;
import com.aiassistant.knowledge.models.response.ProfileResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;

import java.util.List;

@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class KnowledgeMapper {

    private final ObjectMapper objectMapper;

    public ProfileResponse toResponse(BusinessProfileEntity e) {
        return ProfileResponse.builder()
                .id(e.getId())
                .businessId(e.getBusinessId())
                .businessHours(readJson(e.getBusinessHoursJson(), BusinessHours.class))
                .address(e.getAddress())
                .locationNotes(e.getLocationNotes())
                .altPhone(e.getAltPhone())
                .contactEmail(e.getContactEmail())
                .websiteUrl(e.getWebsiteUrl())
                .languagesSpoken(e.getLanguagesSpoken())
                .servicesOffered(readJsonList(e.getServicesOfferedJson()))
                .paymentMethods(e.getPaymentMethods())
                .appointmentPolicy(e.getAppointmentPolicy())
                .cancellationPolicy(e.getCancellationPolicy())
                .refundPolicy(e.getRefundPolicy())
                .completenessScore(e.getCompletenessScore())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public FaqResponse toResponse(BusinessFaqEntity e) {
        return FaqResponse.builder()
                .id(e.getId())
                .businessId(e.getBusinessId())
                .question(e.getQuestion())
                .answer(e.getAnswer())
                .priority(e.getPriority())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public FreeformResponse toResponse(BusinessFreeformEntity e) {
        return FreeformResponse.builder()
                .businessId(e.getBusinessId())
                .content(e.getContent())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public EscalationRuleResponse toResponse(BusinessEscalationRuleEntity e) {
        return EscalationRuleResponse.builder()
                .id(e.getId())
                .businessId(e.getBusinessId())
                .triggerPhrase(e.getTriggerPhrase())
                .action(e.getAction())
                .actionMessage(e.getActionMessage())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .build();
    }

    public String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new AppException("Failed to serialise JSON: " + ex.getMessage());
        }
    }

    public <T> T readJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            throw new AppException("Failed to parse JSON: " + ex.getMessage());
        }
    }

    public List<Service> readJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Service>>() {});
        } catch (Exception ex) {
            throw new AppException("Failed to parse services JSON: " + ex.getMessage());
        }
    }
}
