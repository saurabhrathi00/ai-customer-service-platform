package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.request.RecordUsageRequest;
import com.aiassistant.subscription.services.UsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/subscriptions")
@RequiredArgsConstructor
public class InternalSubscriptionController {

    private final UsageService usageService;

    @PostMapping("/usage")
    public ResponseEntity<Void> recordUsage(@Valid @RequestBody RecordUsageRequest request) {
        usageService.recordUsage(request);
        return ResponseEntity.ok().build();
    }
}
