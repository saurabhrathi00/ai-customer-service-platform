package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.response.SubscriptionResponse;
import com.aiassistant.subscription.services.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{businessId}/current")
    @PreAuthorize("@businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<SubscriptionResponse> getCurrentSubscription(@PathVariable String businessId) {
        return ResponseEntity.ok(subscriptionService.getCurrentSubscription(businessId));
    }

    @PostMapping("/{businessId}/cancel")
    @PreAuthorize("@businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(@PathVariable String businessId) {
        return ResponseEntity.ok(subscriptionService.cancelSubscription(businessId));
    }
}
