package com.aiassistant.userbusiness.services.mapper;

import com.aiassistant.userbusiness.models.dao.BusinessEntity;
import com.aiassistant.userbusiness.models.dao.BusinessPhoneNumberEntity;
import com.aiassistant.userbusiness.models.dao.RatingConfigEntity;
import com.aiassistant.userbusiness.models.response.BusinessResponse;
import com.aiassistant.userbusiness.models.response.PhoneNumberResponse;
import com.aiassistant.userbusiness.models.response.RatingConfigEntryResponse;

public final class BusinessMapper {

    private BusinessMapper() {}

    public static BusinessResponse toResponse(BusinessEntity e) {
        return BusinessResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .email(e.getEmail())
                .category(e.getCategory())
                .description(e.getDescription())
                .location(e.getLocation())
                .operatingHours(e.getOperatingHours())
                .whatsappNumber(e.getWhatsappNumber())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public static PhoneNumberResponse toResponse(BusinessPhoneNumberEntity e) {
        return toResponse(e, null);
    }

    public static PhoneNumberResponse toResponse(BusinessPhoneNumberEntity e, String providerSlug) {
        return PhoneNumberResponse.builder()
                .id(e.getId())
                .businessId(e.getBusinessId())
                .phoneNumber(e.getPhoneNumber())
                .providerId(e.getProviderId())
                .providerSlug(providerSlug)
                .label(e.getLabel())
                .isActive(e.getIsActive())
                .createdAt(e.getCreatedAt())
                .build();
    }

    public static RatingConfigEntryResponse toResponse(RatingConfigEntity e) {
        return RatingConfigEntryResponse.builder()
                .id(e.getId())
                .signalKey(e.getSignalKey())
                .scoreValue(e.getScoreValue())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
