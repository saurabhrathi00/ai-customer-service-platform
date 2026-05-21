package com.aiassistant.knowledge.services;

import com.aiassistant.knowledge.configuration.CacheConfig;
import com.aiassistant.knowledge.models.dao.BusinessFreeformEntity;
import com.aiassistant.knowledge.models.request.FreeformRequest;
import com.aiassistant.knowledge.models.response.FreeformResponse;
import com.aiassistant.knowledge.repository.BusinessFreeformRepository;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FreeformService {

    private final BusinessFreeformRepository repository;
    private final KnowledgeMapper mapper;
    private final ProfileService profileService;

    public FreeformResponse get(String businessId) {
        BusinessFreeformEntity entity = repository.findByBusinessId(businessId)
                .orElseGet(() -> BusinessFreeformEntity.builder()
                        .businessId(businessId)
                        .content(null)
                        .build());
        return mapper.toResponse(entity);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public FreeformResponse upsert(String businessId, FreeformRequest request) {
        BusinessFreeformEntity entity = repository.findByBusinessId(businessId)
                .orElseGet(() -> BusinessFreeformEntity.builder().businessId(businessId).build());
        entity.setContent(request.getContent());
        BusinessFreeformEntity saved = repository.save(entity);
        profileService.recomputeScoreFor(businessId);
        return mapper.toResponse(saved);
    }
}
