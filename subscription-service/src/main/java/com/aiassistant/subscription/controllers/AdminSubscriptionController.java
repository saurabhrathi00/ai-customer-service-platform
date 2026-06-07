package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.request.ManualSubscriptionRequest;
import com.aiassistant.subscription.models.response.SubscriptionResponse;
import com.aiassistant.subscription.services.SubscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<SubscriptionResponse>> getAllSubscriptions() {
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions());
    }

    @PostMapping("/manual")
    public ResponseEntity<SubscriptionResponse> createManualSubscription(
            @Valid @RequestBody ManualSubscriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.createManualSubscription(request));
    }

    @PostMapping("/{businessId}/activate")
    public ResponseEntity<SubscriptionResponse> activateSubscription(@PathVariable String businessId) {
        return ResponseEntity.ok(subscriptionService.activateSubscription(businessId));
    }
}
