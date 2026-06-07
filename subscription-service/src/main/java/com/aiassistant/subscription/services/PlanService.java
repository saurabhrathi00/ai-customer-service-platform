package com.aiassistant.subscription.services;

import com.aiassistant.subscription.exceptions.ConflictException;
import com.aiassistant.subscription.exceptions.NotFoundException;
import com.aiassistant.subscription.models.dao.PlanEntity;
import com.aiassistant.subscription.models.request.CreatePlanRequest;
import com.aiassistant.subscription.models.request.UpdatePlanRequest;
import com.aiassistant.subscription.models.response.PlanResponse;
import com.aiassistant.subscription.repository.PlanRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PlanRepository planRepository;
    private final ObjectMapper objectMapper;

    public List<PlanResponse> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    public List<PlanResponse> getAllPlans() {
        return planRepository.findAllByOrderByDisplayOrderAsc()
                .stream().map(this::toResponse).toList();
    }

    public PlanResponse getPlanBySlug(String slug) {
        return planRepository.findBySlug(slug)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + slug));
    }

    @Transactional
    public PlanResponse createPlan(CreatePlanRequest req) {
        if (planRepository.findBySlug(req.getSlug()).isPresent()) {
            throw new ConflictException("Plan with slug '" + req.getSlug() + "' already exists");
        }
        PlanEntity entity = PlanEntity.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .description(req.getDescription())
                .priceMonthly(req.getPriceMonthly())
                .callsIncluded(req.getCallsIncluded())
                .maxCallDurationSec(req.getMaxCallDurationSec())
                .channels(req.getChannels())
                .phoneNumbers(req.getPhoneNumbers())
                .extraCallRate(req.getExtraCallRate())
                .features(toJson(req.getFeatures()))
                .isActive(false)
                .displayOrder(req.getDisplayOrder())
                .isPopular(req.isPopular())
                .build();
        planRepository.save(entity);
        log.info("Plan created id={} slug={}", entity.getId(), entity.getSlug());
        return toResponse(entity);
    }

    @Transactional
    public PlanResponse updatePlan(String planId, UpdatePlanRequest req) {
        PlanEntity entity = planRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Plan not found: " + planId));

        if (req.getName() != null) entity.setName(req.getName());
        if (req.getDescription() != null) entity.setDescription(req.getDescription());
        if (req.getPriceMonthly() != null) entity.setPriceMonthly(req.getPriceMonthly());
        if (req.getCallsIncluded() != null) entity.setCallsIncluded(req.getCallsIncluded());
        if (req.getMaxCallDurationSec() != null) entity.setMaxCallDurationSec(req.getMaxCallDurationSec());
        if (req.getChannels() != null) entity.setChannels(req.getChannels());
        if (req.getPhoneNumbers() != null) entity.setPhoneNumbers(req.getPhoneNumbers());
        if (req.getExtraCallRate() != null) entity.setExtraCallRate(req.getExtraCallRate());
        if (req.getFeatures() != null) entity.setFeatures(toJson(req.getFeatures()));
        if (req.getIsActive() != null) entity.setActive(req.getIsActive());
        if (req.getDisplayOrder() != null) entity.setDisplayOrder(req.getDisplayOrder());
        if (req.getIsPopular() != null) entity.setPopular(req.getIsPopular());

        planRepository.save(entity);
        log.info("Plan updated id={} slug={}", entity.getId(), entity.getSlug());
        return toResponse(entity);
    }

    PlanResponse toResponse(PlanEntity e) {
        return PlanResponse.builder()
                .id(e.getId())
                .name(e.getName())
                .slug(e.getSlug())
                .description(e.getDescription())
                .priceMonthly(e.getPriceMonthly())
                .callsIncluded(e.getCallsIncluded())
                .maxCallDurationSec(e.getMaxCallDurationSec())
                .channels(e.getChannels())
                .phoneNumbers(e.getPhoneNumbers())
                .extraCallRate(e.getExtraCallRate())
                .features(parseJson(e.getFeatures()))
                .isActive(e.isActive())
                .displayOrder(e.getDisplayOrder())
                .isPopular(e.isPopular())
                .razorpayPlanId(e.getRazorpayPlanId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse features JSON: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
