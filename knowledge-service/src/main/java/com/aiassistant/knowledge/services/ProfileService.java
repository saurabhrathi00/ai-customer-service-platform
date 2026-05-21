package com.aiassistant.knowledge.services;

import com.aiassistant.knowledge.configuration.CacheConfig;
import com.aiassistant.knowledge.exceptions.NotFoundException;
import com.aiassistant.knowledge.exceptions.ValidationFailedException;
import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import com.aiassistant.knowledge.models.domain.BusinessHours;
import com.aiassistant.knowledge.models.domain.Service;
import com.aiassistant.knowledge.models.request.UpsertProfileRequest;
import com.aiassistant.knowledge.models.response.CompletenessResponse;
import com.aiassistant.knowledge.models.response.ProfileResponse;
import com.aiassistant.knowledge.repository.BusinessEscalationRuleRepository;
import com.aiassistant.knowledge.repository.BusinessFaqRepository;
import com.aiassistant.knowledge.repository.BusinessFreeformRepository;
import com.aiassistant.knowledge.repository.BusinessProfileRepository;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import com.aiassistant.knowledge.services.render.CompletenessScorer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final BusinessProfileRepository profileRepository;
    private final BusinessFreeformRepository freeformRepository;
    private final BusinessFaqRepository faqRepository;
    private final BusinessEscalationRuleRepository escalationRepository;
    private final KnowledgeMapper mapper;
    private final CompletenessScorer scorer;

    public ProfileResponse get(String businessId) {
        BusinessProfileEntity entity = profileRepository.findByBusinessId(businessId)
                .orElseThrow(() -> new NotFoundException("Profile not found for business: " + businessId));
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public ProfileResponse upsert(String businessId, UpsertProfileRequest request) {
        validate(request);
        BusinessProfileEntity entity = profileRepository.findByBusinessId(businessId)
                .orElseGet(() -> BusinessProfileEntity.builder().businessId(businessId).build());

        if (request.getBusinessHours() != null) {
            entity.setBusinessHoursJson(mapper.writeJson(request.getBusinessHours()));
        }
        if (request.getServicesOffered() != null) {
            entity.setServicesOfferedJson(mapper.writeJson(request.getServicesOffered()));
        }
        if (request.getAddress() != null) entity.setAddress(request.getAddress());
        if (request.getLocationNotes() != null) entity.setLocationNotes(request.getLocationNotes());
        if (request.getAltPhone() != null) entity.setAltPhone(request.getAltPhone());
        if (request.getContactEmail() != null) entity.setContactEmail(request.getContactEmail());
        if (request.getWebsiteUrl() != null) entity.setWebsiteUrl(request.getWebsiteUrl());
        if (request.getLanguagesSpoken() != null) entity.setLanguagesSpoken(request.getLanguagesSpoken());
        if (request.getPaymentMethods() != null) entity.setPaymentMethods(request.getPaymentMethods());
        if (request.getAppointmentPolicy() != null) entity.setAppointmentPolicy(request.getAppointmentPolicy());
        if (request.getCancellationPolicy() != null) entity.setCancellationPolicy(request.getCancellationPolicy());
        if (request.getRefundPolicy() != null) entity.setRefundPolicy(request.getRefundPolicy());

        BusinessProfileEntity saved = profileRepository.save(entity);
        recomputeScore(saved);
        BusinessProfileEntity updated = profileRepository.save(saved);
        log.info("Profile upserted businessId={} score={}", businessId, updated.getCompletenessScore());
        return mapper.toResponse(updated);
    }

    public CompletenessResponse completeness(String businessId) {
        BusinessProfileEntity profile = profileRepository.findByBusinessId(businessId).orElse(null);
        BusinessFreeformEntity freeform = freeformRepository.findByBusinessId(businessId).orElse(null);
        long faqs = faqRepository.countByBusinessIdAndIsActive(businessId, true);
        long rules = escalationRepository.findAllByBusinessIdAndIsActiveOrderByCreatedAtAsc(businessId, true).size();
        CompletenessScorer.Result r = scorer.score(profile, freeform, faqs, rules);
        return CompletenessResponse.builder()
                .businessId(businessId)
                .score(r.score())
                .missingFields(r.missingFields())
                .build();
    }

    /**
     * Recompute and stamp the score on the profile. Called by ProfileService.upsert
     * itself and by sibling services (FAQ / freeform / escalation) when they mutate.
     */
    @Transactional
    public void recomputeScoreFor(String businessId) {
        profileRepository.findByBusinessId(businessId).ifPresent(profile -> {
            recomputeScore(profile);
            profileRepository.save(profile);
        });
    }

    private void recomputeScore(BusinessProfileEntity profile) {
        BusinessFreeformEntity freeform = freeformRepository.findByBusinessId(profile.getBusinessId()).orElse(null);
        long faqs = faqRepository.countByBusinessIdAndIsActive(profile.getBusinessId(), true);
        long rules = escalationRepository
                .findAllByBusinessIdAndIsActiveOrderByCreatedAtAsc(profile.getBusinessId(), true).size();
        profile.setCompletenessScore(scorer.score(profile, freeform, faqs, rules).score());
    }

    // -- validation beyond Bean Validation ---------------------------------

    private void validate(UpsertProfileRequest req) {
        Map<String, String> errs = new LinkedHashMap<>();
        validateHours(req.getBusinessHours(), errs);
        validateServices(req.getServicesOffered(), errs);
        if (!errs.isEmpty()) throw new ValidationFailedException("Validation failed", errs);
    }

    private static void validateHours(BusinessHours h, Map<String, String> errs) {
        if (h == null || h.getDays() == null) return;
        h.getDays().forEach((day, dh) -> {
            if (dh == null || Boolean.TRUE.equals(dh.getClosed())) return;
            if (dh.getOpen() == null || dh.getClose() == null) {
                errs.put("business_hours." + day, "open and close required when not closed");
                return;
            }
            if (dh.getOpen().compareTo(dh.getClose()) >= 0) {
                errs.put("business_hours." + day, "open must be earlier than close");
            }
        });
    }

    private static void validateServices(List<Service> services, Map<String, String> errs) {
        if (services == null) return;
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            if (s == null) continue;
            String prefix = "services_offered[" + i + "]";
            if (s.getPriceMin() != null && s.getPriceMax() != null && s.getPriceMin() > s.getPriceMax()) {
                errs.put(prefix + ".price", "priceMin must be ≤ priceMax");
            }
            if (s.getDurationMinutes() != null && s.getDurationMinutes() <= 0) {
                errs.put(prefix + ".durationMinutes", "must be > 0");
            }
        }
    }
}
