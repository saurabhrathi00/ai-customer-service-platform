package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.request.UpdateLeadNotificationSettingsRequest;
import com.aiassistant.userbusiness.models.response.LeadNotificationSettingsResponse;
import com.aiassistant.userbusiness.services.LeadNotificationSettingsService;
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
@RequestMapping("/api/v1/business/{id}/lead-settings")
@RequiredArgsConstructor
public class LeadNotificationSettingsController {

    private final LeadNotificationSettingsService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_leads.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadNotificationSettingsResponse> get(@PathVariable("id") String id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('SCOPE_leads.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadNotificationSettingsResponse> update(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateLeadNotificationSettingsRequest req) {
        return ResponseEntity.ok(service.update(id, req));
    }
}
