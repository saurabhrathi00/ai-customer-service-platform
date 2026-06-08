package com.aiassistant.subscription.services;

import com.aiassistant.subscription.models.dao.SubscriptionEntity;
import com.aiassistant.subscription.models.dao.UsageEventEntity;
import com.aiassistant.subscription.models.request.RecordUsageRequest;
import com.aiassistant.subscription.repository.SubscriptionRepository;
import com.aiassistant.subscription.repository.UsageEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsageEventRepository usageEventRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public void recordUsage(RecordUsageRequest request) {
        if (usageEventRepository.existsByCallId(request.getCallId())) {
            log.info("Usage already recorded for callId={}, skipping", request.getCallId());
            return;
        }

        SubscriptionEntity subscription = subscriptionRepository
                .findByBusinessIdAndStatusIn(request.getBusinessId(), List.of("ACTIVE", "PAST_DUE"))
                .orElse(null);

        UsageEventEntity event = UsageEventEntity.builder()
                .businessId(request.getBusinessId())
                .subscriptionId(subscription != null ? subscription.getId() : null)
                .callId(request.getCallId())
                .callDurationSecs(request.getCallDurationSecs())
                .build();

        try {
            usageEventRepository.save(event);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate usage event for callId={}, ignoring", request.getCallId());
            return;
        }

        if (subscription != null) {
            subscription.setCallsUsed(subscription.getCallsUsed() + 1);
            subscription.setMinutesUsed(subscription.getMinutesUsed()
                    + (int) Math.ceil(request.getCallDurationSecs() / 60.0));
            subscriptionRepository.save(subscription);
        }

        log.info("Usage recorded businessId={} callId={} durationSecs={} subscriptionId={}",
                request.getBusinessId(), request.getCallId(), request.getCallDurationSecs(),
                subscription != null ? subscription.getId() : "none");
    }
}
