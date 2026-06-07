package com.aiassistant.subscription.services;

import com.aiassistant.subscription.models.dao.PaymentEntity;
import com.aiassistant.subscription.models.dao.SubscriptionEntity;
import com.aiassistant.subscription.repository.PaymentRepository;
import com.aiassistant.subscription.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handleWebhook(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText();
            JsonNode payloadNode = root.path("payload");

            log.info("Processing Razorpay webhook: {}", event);

            switch (event) {
                case "subscription.activated" -> handleSubscriptionActivated(payloadNode);
                case "subscription.charged" -> handleSubscriptionCharged(payloadNode);
                case "subscription.cancelled" -> handleSubscriptionCancelled(payloadNode);
                case "subscription.halted" -> handleSubscriptionHalted(payloadNode);
                case "payment.captured" -> handlePaymentCaptured(payloadNode);
                case "payment.failed" -> handlePaymentFailed(payloadNode);
                default -> log.info("Ignoring unhandled webhook event: {}", event);
            }
        } catch (Exception e) {
            log.error("Error processing webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Webhook processing failed", e);
        }
    }

    private void handleSubscriptionActivated(JsonNode payload) {
        String rzpSubId = payload.path("subscription").path("entity").path("id").asText();
        subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresentOrElse(sub -> {
            sub.setStatus("PENDING_SETUP");
            long start = payload.path("subscription").path("entity").path("current_start").asLong();
            long end = payload.path("subscription").path("entity").path("current_end").asLong();
            if (start > 0) sub.setCurrentPeriodStart(Instant.ofEpochSecond(start));
            if (end > 0) sub.setCurrentPeriodEnd(Instant.ofEpochSecond(end));
            subscriptionRepository.save(sub);
            log.info("Subscription activated → PENDING_SETUP: {}", rzpSubId);
        }, () -> log.warn("Subscription not found for activation: {}", rzpSubId));
    }

    private void handleSubscriptionCharged(JsonNode payload) {
        String rzpSubId = payload.path("subscription").path("entity").path("id").asText();
        subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresentOrElse(sub -> {
            long start = payload.path("subscription").path("entity").path("current_start").asLong();
            long end = payload.path("subscription").path("entity").path("current_end").asLong();
            if (start > 0) sub.setCurrentPeriodStart(Instant.ofEpochSecond(start));
            if (end > 0) sub.setCurrentPeriodEnd(Instant.ofEpochSecond(end));
            sub.setCallsUsed(0);
            subscriptionRepository.save(sub);
            log.info("Subscription charged, period reset: {}", rzpSubId);
        }, () -> log.warn("Subscription not found for charge: {}", rzpSubId));
    }

    private void handleSubscriptionCancelled(JsonNode payload) {
        String rzpSubId = payload.path("subscription").path("entity").path("id").asText();
        subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresentOrElse(sub -> {
            sub.setStatus("CANCELLED");
            sub.setCancelledAt(Instant.now());
            subscriptionRepository.save(sub);
            log.info("Subscription cancelled: {}", rzpSubId);
        }, () -> log.warn("Subscription not found for cancellation: {}", rzpSubId));
    }

    private void handleSubscriptionHalted(JsonNode payload) {
        String rzpSubId = payload.path("subscription").path("entity").path("id").asText();
        subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).ifPresentOrElse(sub -> {
            sub.setStatus("PAST_DUE");
            subscriptionRepository.save(sub);
            log.info("Subscription halted → PAST_DUE: {}", rzpSubId);
        }, () -> log.warn("Subscription not found for halt: {}", rzpSubId));
    }

    private void handlePaymentCaptured(JsonNode payload) {
        JsonNode paymentNode = payload.path("payment").path("entity");
        String rzpPaymentId = paymentNode.path("id").asText();

        if (paymentRepository.existsByRazorpayPaymentId(rzpPaymentId)) {
            log.info("Duplicate payment webhook ignored: {}", rzpPaymentId);
            return;
        }

        String rzpSubId = paymentNode.path("subscription_id").asText(null);
        SubscriptionEntity sub = rzpSubId != null
                ? subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).orElse(null)
                : null;

        int amount = paymentNode.path("amount").asInt();
        int gstAmount = (int) Math.round(amount * 18.0 / 118.0);

        PaymentEntity payment = PaymentEntity.builder()
                .businessId(sub != null ? sub.getBusinessId() : null)
                .subscriptionId(sub != null ? sub.getId() : null)
                .razorpayPaymentId(rzpPaymentId)
                .razorpayOrderId(paymentNode.path("order_id").asText(null))
                .amount(amount)
                .currency(paymentNode.path("currency").asText("INR"))
                .status("CAPTURED")
                .paymentMethod(paymentNode.path("method").asText(null))
                .gstAmount(gstAmount)
                .build();
        paymentRepository.save(payment);
        log.info("Payment captured: {} amount={}", rzpPaymentId, amount);
    }

    private void handlePaymentFailed(JsonNode payload) {
        JsonNode paymentNode = payload.path("payment").path("entity");
        String rzpPaymentId = paymentNode.path("id").asText();

        if (paymentRepository.existsByRazorpayPaymentId(rzpPaymentId)) {
            log.info("Duplicate failed payment webhook ignored: {}", rzpPaymentId);
            return;
        }

        String rzpSubId = paymentNode.path("subscription_id").asText(null);
        SubscriptionEntity sub = rzpSubId != null
                ? subscriptionRepository.findByRazorpaySubscriptionId(rzpSubId).orElse(null)
                : null;

        PaymentEntity payment = PaymentEntity.builder()
                .businessId(sub != null ? sub.getBusinessId() : null)
                .subscriptionId(sub != null ? sub.getId() : null)
                .razorpayPaymentId(rzpPaymentId)
                .razorpayOrderId(paymentNode.path("order_id").asText(null))
                .amount(paymentNode.path("amount").asInt())
                .currency(paymentNode.path("currency").asText("INR"))
                .status("FAILED")
                .paymentMethod(paymentNode.path("method").asText(null))
                .gstAmount(0)
                .build();
        paymentRepository.save(payment);
        log.info("Payment failed: {}", rzpPaymentId);
    }

}
