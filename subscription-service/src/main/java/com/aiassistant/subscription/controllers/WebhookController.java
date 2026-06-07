package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.services.RazorpayService;
import com.aiassistant.subscription.services.WebhookService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;
    private final RazorpayService razorpayService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (signature == null || !razorpayService.verifyWebhookSignature(payload, signature)) {
            log.warn("Razorpay webhook signature verification failed");
            return ResponseEntity.badRequest().build();
        }

        webhookService.handleWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
