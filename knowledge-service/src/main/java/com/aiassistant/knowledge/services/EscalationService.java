package com.aiassistant.knowledge.services;

import com.aiassistant.knowledge.configuration.CacheConfig;
import com.aiassistant.knowledge.exceptions.NotFoundException;
import com.aiassistant.knowledge.models.dao.BusinessEscalationRuleEntity;
import com.aiassistant.knowledge.models.request.EscalationRuleRequest;
import com.aiassistant.knowledge.models.response.EscalationRuleResponse;
import com.aiassistant.knowledge.repository.BusinessEscalationRuleRepository;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EscalationService {

    private final BusinessEscalationRuleRepository repository;
    private final KnowledgeMapper mapper;
    private final ProfileService profileService;

    public List<EscalationRuleResponse> list(String businessId) {
        return repository.findAllByBusinessIdOrderByCreatedAtAsc(businessId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public EscalationRuleResponse create(String businessId, EscalationRuleRequest request) {
        BusinessEscalationRuleEntity entity = BusinessEscalationRuleEntity.builder()
                .businessId(businessId)
                .triggerPhrase(request.getTriggerPhrase().trim())
                .action(request.getAction())
                .actionMessage(request.getActionMessage())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();
        BusinessEscalationRuleEntity saved = repository.save(entity);
        profileService.recomputeScoreFor(businessId);
        return mapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public EscalationRuleResponse update(String businessId, String ruleId, EscalationRuleRequest request) {
        BusinessEscalationRuleEntity entity = load(businessId, ruleId);
        entity.setTriggerPhrase(request.getTriggerPhrase().trim());
        entity.setAction(request.getAction());
        entity.setActionMessage(request.getActionMessage());
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
        BusinessEscalationRuleEntity saved = repository.save(entity);
        profileService.recomputeScoreFor(businessId);
        return mapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public void delete(String businessId, String ruleId) {
        BusinessEscalationRuleEntity entity = load(businessId, ruleId);
        repository.delete(entity);
        profileService.recomputeScoreFor(businessId);
    }

    private BusinessEscalationRuleEntity load(String businessId, String ruleId) {
        return repository.findByIdAndBusinessId(ruleId, businessId)
                .orElseThrow(() -> new NotFoundException(
                        "Escalation rule " + ruleId + " not found for business " + businessId));
    }
}
