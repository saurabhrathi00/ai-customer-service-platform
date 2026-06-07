package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.request.CheckoutRequest;
import com.aiassistant.subscription.models.response.CheckoutResponse;
import com.aiassistant.subscription.services.CheckoutService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(checkoutService.createCheckout(request));
    }
}
