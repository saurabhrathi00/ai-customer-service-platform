package com.aiassistant.subscription.services;

import com.aiassistant.subscription.configuration.SecretsConfiguration;
import com.aiassistant.subscription.configuration.ServiceConfiguration;
import com.razorpay.Plan;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Subscription;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    private static final Logger log = LoggerFactory.getLogger(RazorpayService.class);

    private final SecretsConfiguration secrets;
    private final ServiceConfiguration configs;
    private RazorpayClient razorpayClient;

    @PostConstruct
    void init() {
        String keyId = secrets.getRazorpay() != null ? secrets.getRazorpay().getKeyId() : null;
        String keySecret = secrets.getRazorpay() != null ? secrets.getRazorpay().getKeySecret() : null;
        if (keyId == null || keyId.isBlank() || keyId.equals("REPLACE_ME")) {
            log.warn("Razorpay credentials not configured — payment features disabled. Manual subscriptions still work.");
            return;
        }
        try {
            razorpayClient = new RazorpayClient(keyId, keySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialise Razorpay client: {}", e.getMessage());
        }
    }

    private void requireClient() {
        if (razorpayClient == null) {
            throw new IllegalStateException("Razorpay is not configured. Set razorpay credentials in secrets.properties.");
        }
    }

    public String createRazorpayPlan(String planName, int amountPaise, String period, int interval) {
        requireClient();
        try {
            JSONObject item = new JSONObject();
            item.put("name", planName);
            item.put("amount", amountPaise);
            item.put("currency", "INR");

            JSONObject request = new JSONObject();
            request.put("period", period);
            request.put("interval", interval);
            request.put("item", item);

            Plan plan = razorpayClient.plans.create(request);
            String razorpayPlanId = plan.get("id");
            log.info("Razorpay plan created: {} for {}", razorpayPlanId, planName);
            return razorpayPlanId;
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay plan: {}", e.getMessage());
            throw new RuntimeException("Razorpay plan creation failed", e);
        }
    }

    public Subscription createSubscription(String razorpayPlanId, int totalCount) {
        requireClient();
        try {
            JSONObject request = new JSONObject();
            request.put("plan_id", razorpayPlanId);
            request.put("total_count", totalCount);
            request.put("quantity", 1);

            Subscription subscription = razorpayClient.subscriptions.create(request);
            log.info("Razorpay subscription created: {}", (String) subscription.get("id"));
            return subscription;
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay subscription: {}", e.getMessage());
            throw new RuntimeException("Razorpay subscription creation failed", e);
        }
    }

    public void cancelSubscription(String razorpaySubscriptionId, boolean cancelAtCycleEnd) {
        requireClient();
        try {
            JSONObject request = new JSONObject();
            request.put("cancel_at_cycle_end", cancelAtCycleEnd ? 1 : 0);
            razorpayClient.subscriptions.cancel(razorpaySubscriptionId, request);
            log.info("Razorpay subscription cancelled: {} (at_cycle_end={})",
                    razorpaySubscriptionId, cancelAtCycleEnd);
        } catch (RazorpayException e) {
            log.error("Failed to cancel Razorpay subscription: {}", e.getMessage());
            throw new RuntimeException("Razorpay subscription cancellation failed", e);
        }
    }

    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            String webhookSecret = secrets.getRazorpay().getWebhookSecret();
            return Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            log.warn("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public String getKeyId() {
        return secrets.getRazorpay().getKeyId();
    }
}
