package com.aiassistant.knowledge.services;

import com.aiassistant.knowledge.configuration.CacheConfig;
import com.aiassistant.knowledge.exceptions.ConflictException;
import com.aiassistant.knowledge.exceptions.NotFoundException;
import com.aiassistant.knowledge.models.dao.BusinessFaqEntity;
import com.aiassistant.knowledge.models.request.FaqRequest;
import com.aiassistant.knowledge.models.response.FaqResponse;
import com.aiassistant.knowledge.repository.BusinessFaqRepository;
import com.aiassistant.knowledge.services.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaqService {

    private static final Logger log = LoggerFactory.getLogger(FaqService.class);
    private static final long MAX_ACTIVE_FAQS = 50;

    private final BusinessFaqRepository repository;
    private final KnowledgeMapper mapper;
    private final ProfileService profileService;

    public List<FaqResponse> list(String businessId) {
        return repository.findAllByBusinessIdOrderByPriorityDescCreatedAtAsc(businessId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public FaqResponse create(String businessId, FaqRequest request) {
        boolean willBeActive = request.getIsActive() == null || request.getIsActive();
        if (willBeActive
                && repository.countByBusinessIdAndIsActive(businessId, true) >= MAX_ACTIVE_FAQS) {
            throw new ConflictException("Maximum of " + MAX_ACTIVE_FAQS + " active FAQs per business");
        }
        BusinessFaqEntity entity = BusinessFaqEntity.builder()
                .businessId(businessId)
                .question(request.getQuestion().trim())
                .answer(request.getAnswer().trim())
                .priority(request.getPriority() == null ? 0 : request.getPriority())
                .isActive(willBeActive)
                .build();
        BusinessFaqEntity saved = repository.save(entity);
        profileService.recomputeScoreFor(businessId);
        log.info("FAQ created businessId={} id={}", businessId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public FaqResponse update(String businessId, String faqId, FaqRequest request) {
        BusinessFaqEntity entity = load(businessId, faqId);
        boolean nowActive = request.getIsActive() == null ? entity.getIsActive() : request.getIsActive();
        if (nowActive && !Boolean.TRUE.equals(entity.getIsActive())
                && repository.countByBusinessIdAndIsActive(businessId, true) >= MAX_ACTIVE_FAQS) {
            throw new ConflictException("Maximum of " + MAX_ACTIVE_FAQS + " active FAQs per business");
        }
        entity.setQuestion(request.getQuestion().trim());
        entity.setAnswer(request.getAnswer().trim());
        if (request.getPriority() != null) entity.setPriority(request.getPriority());
        entity.setIsActive(nowActive);
        BusinessFaqEntity saved = repository.save(entity);
        profileService.recomputeScoreFor(businessId);
        return mapper.toResponse(saved);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_RENDERED, key = "#businessId")
    public void delete(String businessId, String faqId) {
        BusinessFaqEntity entity = load(businessId, faqId);
        repository.delete(entity);
        profileService.recomputeScoreFor(businessId);
        log.info("FAQ deleted businessId={} id={}", businessId, faqId);
    }

    private BusinessFaqEntity load(String businessId, String faqId) {
        return repository.findByIdAndBusinessId(faqId, businessId)
                .orElseThrow(() -> new NotFoundException("FAQ " + faqId + " not found for business " + businessId));
    }
}
