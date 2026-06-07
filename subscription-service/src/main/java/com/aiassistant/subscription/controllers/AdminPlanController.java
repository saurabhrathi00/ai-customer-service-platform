package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.request.CreatePlanRequest;
import com.aiassistant.subscription.models.request.UpdatePlanRequest;
import com.aiassistant.subscription.models.response.PlanResponse;
import com.aiassistant.subscription.services.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminPlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        return ResponseEntity.ok(planService.getAllPlans());
    }

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request));
    }

    @PutMapping("/{planId}")
    public ResponseEntity<PlanResponse> updatePlan(@PathVariable String planId,
                                                    @Valid @RequestBody UpdatePlanRequest request) {
        return ResponseEntity.ok(planService.updatePlan(planId, request));
    }
}
