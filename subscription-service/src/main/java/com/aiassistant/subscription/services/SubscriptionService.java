package com.aiassistant.subscription.services;

import com.aiassistant.subscription.exceptions.ConflictException;
import com.aiassistant.subscription.exceptions.NotFoundException;
import com.aiassistant.subscription.models.dao.PlanEntity;
import com.aiassistant.subscription.models.dao.SubscriptionEntity;
import com.aiassistant.subscription.clients.UserBusinessClient;
import com.aiassistant.subscription.models.request.ManualSubscriptionRequest;
import com.aiassistant.subscription.models.response.PlanResponse;
import com.aiassistant.subscription.models.response.SubscriptionResponse;
import com.aiassistant.subscription.repository.PlanRepository;
import com.aiassistant.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PlanService planService;
    private final RazorpayService razorpayService;
    private final UserBusinessClient userBusinessClient;

    public SubscriptionResponse getCurrentSubscription(String businessId) {
        SubscriptionEntity sub = subscriptionRepository.findByBusinessIdAndStatusIn(
                        businessId, List.of("ACTIVE", "PENDING_SETUP", "PENDING_PAYMENT", "PAST_DUE"))
                .orElseThrow(() -> new NotFoundException("No active subscription for business: " + businessId));

        PlanEntity plan = planRepository.findById(sub.getPlanId())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + sub.getPlanId()));

        PlanResponse planResponse = planService.toResponse(plan);
        return toResponse(sub, planResponse);
    }

    @Transactional
    public SubscriptionResponse cancelSubscription(String businessId) {
        SubscriptionEntity sub = subscriptionRepository.findByBusinessIdAndStatusIn(
                        businessId, List.of("ACTIVE"))
                .orElseThrow(() -> new NotFoundException("No active subscription to cancel"));

        if (sub.getRazorpaySubscriptionId() != null) {
            razorpayService.cancelSubscription(sub.getRazorpaySubscriptionId(), true);
        }

        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(Instant.now());
        subscriptionRepository.save(sub);

        log.info("Subscription cancel requested: {} for business {}", sub.getId(), businessId);

        PlanEntity plan = planRepository.findById(sub.getPlanId()).orElseThrow();
        return toResponse(sub, planService.toResponse(plan));
    }

    @Transactional
    public SubscriptionResponse activateSubscription(String businessId) {
        SubscriptionEntity sub = subscriptionRepository.findByBusinessIdAndStatusIn(
                        businessId, List.of("PENDING_SETUP"))
                .orElseThrow(() -> new NotFoundException("No subscription pending setup for business: " + businessId));

        sub.setStatus("ACTIVE");
        if (sub.getCurrentPeriodStart() == null) {
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
        }
        subscriptionRepository.save(sub);

        try {
            userBusinessClient.updateSubscriptionStatus(businessId, "ACTIVE", sub.getId());
        } catch (Exception e) {
            log.error("Failed to update business subscription status: {}", e.getMessage());
        }

        log.info("Subscription activated: {} for business {}", sub.getId(), businessId);

        PlanEntity plan = planRepository.findById(sub.getPlanId()).orElseThrow();
        return toResponse(sub, planService.toResponse(plan));
    }

    @Transactional
    public SubscriptionResponse createManualSubscription(ManualSubscriptionRequest req) {
        PlanEntity plan = planRepository.findBySlug(req.getPlanSlug())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + req.getPlanSlug()));

        subscriptionRepository.findByBusinessIdAndStatusIn(req.getBusinessId(),
                        List.of("ACTIVE", "PENDING_SETUP", "PENDING_PAYMENT"))
                .ifPresent(existing -> {
                    throw new ConflictException("Business already has an active subscription");
                });

        SubscriptionEntity entity = SubscriptionEntity.builder()
                .businessId(req.getBusinessId())
                .planId(plan.getId())
                .status("ACTIVE")
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        subscriptionRepository.save(entity);

        try {
            userBusinessClient.updateSubscriptionStatus(req.getBusinessId(), "ACTIVE", entity.getId());
        } catch (Exception e) {
            log.error("Failed to update business subscription status: {}", e.getMessage());
        }

        log.info("Manual subscription created: {} for business {} on plan {}",
                entity.getId(), req.getBusinessId(), plan.getSlug());

        return toResponse(entity, planService.toResponse(plan));
    }

    public List<SubscriptionResponse> getAllSubscriptions() {
        return subscriptionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(sub -> {
                    PlanEntity plan = planRepository.findById(sub.getPlanId()).orElse(null);
                    PlanResponse planResponse = plan != null ? planService.toResponse(plan) : null;
                    return toResponse(sub, planResponse);
                })
                .toList();
    }

    private SubscriptionResponse toResponse(SubscriptionEntity sub, PlanResponse plan) {
        int callsRemaining = plan != null
                ? Math.max(0, plan.getCallsIncluded() - sub.getCallsUsed())
                : 0;

        long daysRemaining = sub.getCurrentPeriodEnd() != null
                ? Math.max(0, ChronoUnit.DAYS.between(Instant.now(), sub.getCurrentPeriodEnd()))
                : 0;

        return SubscriptionResponse.builder()
                .id(sub.getId())
                .businessId(sub.getBusinessId())
                .plan(plan)
                .status(sub.getStatus())
                .razorpaySubscriptionId(sub.getRazorpaySubscriptionId())
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .callsUsed(sub.getCallsUsed())
                .minutesUsed(sub.getMinutesUsed())
                .callsRemaining(callsRemaining)
                .daysRemaining((int) daysRemaining)
                .cancelAtPeriodEnd(sub.isCancelAtPeriodEnd())
                .cancelledAt(sub.getCancelledAt())
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();
    }
}
