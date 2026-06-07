package com.aiassistant.subscription.services;

import com.aiassistant.subscription.exceptions.ConflictException;
import com.aiassistant.subscription.exceptions.NotFoundException;
import com.aiassistant.subscription.models.dao.PlanEntity;
import com.aiassistant.subscription.models.dao.SubscriptionEntity;
import com.aiassistant.subscription.models.request.CheckoutRequest;
import com.aiassistant.subscription.models.response.CheckoutResponse;
import com.aiassistant.subscription.repository.PlanRepository;
import com.aiassistant.subscription.repository.SubscriptionRepository;
import com.razorpay.Subscription;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RazorpayService razorpayService;

    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest req) {
        PlanEntity plan = planRepository.findBySlug(req.getPlanSlug())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + req.getPlanSlug()));

        if (!plan.isActive()) {
            throw new NotFoundException("Plan is not available: " + req.getPlanSlug());
        }

        if (plan.getRazorpayPlanId() == null || plan.getRazorpayPlanId().isBlank()) {
            throw new IllegalStateException("Plan not synced with Razorpay: " + plan.getSlug());
        }

        if (req.getBusinessId() != null) {
            subscriptionRepository.findByBusinessIdAndStatusIn(req.getBusinessId(),
                            List.of("ACTIVE", "PENDING_SETUP"))
                    .ifPresent(existing -> {
                        throw new ConflictException("Business already has an active subscription");
                    });
        }

        Subscription rzpSub = razorpayService.createSubscription(plan.getRazorpayPlanId(), 12);

        SubscriptionEntity entity = SubscriptionEntity.builder()
                .businessId(req.getBusinessId())
                .planId(plan.getId())
                .status("PENDING_PAYMENT")
                .razorpaySubscriptionId(rzpSub.get("id"))
                .build();
        subscriptionRepository.save(entity);

        log.info("Checkout created subscription={} razorpay={} plan={}",
                entity.getId(), rzpSub.get("id"), plan.getSlug());

        return CheckoutResponse.builder()
                .subscriptionId(entity.getId())
                .razorpaySubscriptionId(rzpSub.get("id"))
                .razorpayKeyId(razorpayService.getKeyId())
                .build();
    }
}
