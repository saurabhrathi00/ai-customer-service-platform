package com.aiassistant.knowledge.services;

import com.aiassistant.knowledge.configuration.CacheConfig;
import com.aiassistant.knowledge.models.dao.BusinessEscalationRuleEntity;
import com.aiassistant.knowledge.models.dao.BusinessFaqEntity;
import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.dao.BusinessProfileEntity;
import com.aiassistant.knowledge.models.response.RawKnowledgeResponse;
import com.aiassistant.knowledge.models.response.RenderedKnowledgeResponse;
import com.aiassistant.knowledge.repository.BusinessEscalationRuleRepository;
import com.aiassistant.knowledge.repository.BusinessFaqRepository;
import com.aiassistant.knowledge.repository.BusinessFreeformRepository;
import com.aiassistant.knowledge.repository.BusinessProfileRepository;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import com.aiassistant.knowledge.services.render.KnowledgeRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side service for the internal-facing rendered + raw endpoints. The
 * rendered output is cached by businessId; any mutation in Profile/FAQ/
 * Freeform/Escalation services evicts the entry.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KnowledgeService {

    private final BusinessProfileRepository profileRepository;
    private final BusinessFreeformRepository freeformRepository;
    private final BusinessFaqRepository faqRepository;
    private final BusinessEscalationRuleRepository escalationRepository;
    private final KnowledgeRenderer renderer;
    private final KnowledgeMapper mapper;

    @Cacheable(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public RenderedKnowledgeResponse rendered(String businessId, String businessNameForHeader) {
        BusinessProfileEntity profile = profileRepository.findByBusinessId(businessId).orElse(null);
        BusinessFreeformEntity freeform = freeformRepository.findByBusinessId(businessId).orElse(null);
        List<BusinessFaqEntity> faqs = faqRepository
                .findAllByBusinessIdAndIsActiveOrderByPriorityDescCreatedAtAsc(businessId, true);
        List<BusinessEscalationRuleEntity> rules = escalationRepository
                .findAllByBusinessIdAndIsActiveOrderByCreatedAtAsc(businessId, true);

        String text = renderer.render(businessNameForHeader, profile, freeform, faqs, rules);
        return RenderedKnowledgeResponse.builder()
                .businessId(businessId)
                .text(text)
                .completenessScore(profile == null ? 0 : profile.getCompletenessScore())
                .build();
    }

    public RawKnowledgeResponse raw(String businessId) {
        BusinessProfileEntity profile = profileRepository.findByBusinessId(businessId).orElse(null);
        BusinessFreeformEntity freeform = freeformRepository.findByBusinessId(businessId).orElse(null);
        List<BusinessFaqEntity> faqs = faqRepository.findAllByBusinessIdOrderByPriorityDescCreatedAtAsc(businessId);
        List<BusinessEscalationRuleEntity> rules = escalationRepository.findAllByBusinessIdOrderByCreatedAtAsc(businessId);

        return RawKnowledgeResponse.builder()
                .businessId(businessId)
                .profile(profile == null ? null : mapper.toResponse(profile))
                .freeform(freeform == null ? null : mapper.toResponse(freeform))
                .faqs(faqs.stream().map(mapper::toResponse).toList())
                .escalations(rules.stream().map(mapper::toResponse).toList())
                .build();
    }
}
