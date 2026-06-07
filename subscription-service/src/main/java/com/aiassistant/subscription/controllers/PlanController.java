package com.aiassistant.subscription.controllers;

import com.aiassistant.subscription.models.response.PlanResponse;
import com.aiassistant.subscription.services.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getActivePlans() {
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PlanResponse> getPlanBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(planService.getPlanBySlug(slug));
    }
}
