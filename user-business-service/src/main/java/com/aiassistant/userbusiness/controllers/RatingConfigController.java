package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.request.UpdateRatingConfigRequest;
import com.aiassistant.userbusiness.models.response.RatingConfigResponse;
import com.aiassistant.userbusiness.services.RatingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/{id}/rating-config")
@RequiredArgsConstructor
public class RatingConfigController {

    private final RatingConfigService ratingConfigService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_business.read') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<RatingConfigResponse> get(@PathVariable("id") String businessId) {
        return ResponseEntity.ok(ratingConfigService.get(businessId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<RatingConfigResponse> update(@PathVariable("id") String businessId,
                                                       @Valid @RequestBody UpdateRatingConfigRequest request) {
        return ResponseEntity.ok(ratingConfigService.update(businessId, request));
    }
}
