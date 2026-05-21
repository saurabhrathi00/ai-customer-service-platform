package com.aiassistant.userbusiness.services;

import com.aiassistant.userbusiness.exceptions.BusinessNotFoundException;
import com.aiassistant.userbusiness.models.dao.RatingConfigEntity;
import com.aiassistant.userbusiness.models.request.UpdateRatingConfigRequest;
import com.aiassistant.userbusiness.models.response.RatingConfigEntryResponse;
import com.aiassistant.userbusiness.models.response.RatingConfigResponse;
import com.aiassistant.userbusiness.repository.BusinessRepository;
import com.aiassistant.userbusiness.repository.RatingConfigRepository;
import com.aiassistant.userbusiness.services.mapper.BusinessMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RatingConfigService {

    private static final Logger log = LoggerFactory.getLogger(RatingConfigService.class);

    private final RatingConfigRepository ratingConfigRepository;
    private final BusinessRepository businessRepository;

    public RatingConfigResponse get(String businessId) {
        assertBusinessExists(businessId);
        List<RatingConfigEntryResponse> entries = ratingConfigRepository
                .findAllByBusinessId(businessId).stream()
                .map(BusinessMapper::toResponse)
                .toList();
        return RatingConfigResponse.builder()
                .businessId(businessId)
                .entries(entries)
                .build();
    }

    @Transactional
    public RatingConfigResponse update(String businessId, UpdateRatingConfigRequest request) {
        assertBusinessExists(businessId);
        for (UpdateRatingConfigRequest.Entry entry : request.getEntries()) {
            RatingConfigEntity existing = ratingConfigRepository
                    .findByBusinessIdAndSignalKey(businessId, entry.getSignalKey())
                    .orElseGet(() -> RatingConfigEntity.builder()
                            .businessId(businessId)
                            .signalKey(entry.getSignalKey())
                            .build());
            existing.setScoreValue(entry.getScoreValue());
            ratingConfigRepository.save(existing);
        }
        log.info("Rating config updated businessId={} entries={}", businessId, request.getEntries().size());
        return get(businessId);
    }

    private void assertBusinessExists(String businessId) {
        if (!businessRepository.existsById(businessId)) {
            throw new BusinessNotFoundException("Business not found: " + businessId);
        }
    }
}
