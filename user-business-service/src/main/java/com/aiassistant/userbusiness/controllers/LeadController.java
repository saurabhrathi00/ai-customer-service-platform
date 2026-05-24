package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.enums.LeadDecisionChannel;
import com.aiassistant.userbusiness.models.request.ApproveLeadRequest;
import com.aiassistant.userbusiness.models.request.DeclineLeadRequest;
import com.aiassistant.userbusiness.models.response.LeadResponse;
import com.aiassistant.userbusiness.services.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/{id}/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_leads.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<List<LeadResponse>> list(@PathVariable("id") String id) {
        return ResponseEntity.ok(leadService.listForBusiness(id));
    }

    @GetMapping("/{leadId}")
    @PreAuthorize("hasAuthority('SCOPE_leads.read') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadResponse> get(
            @PathVariable("id") String id,
            @PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(leadService.get(id, leadId));
    }

    @PostMapping("/{leadId}/approve")
    @PreAuthorize("hasAuthority('SCOPE_leads.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadResponse> approve(
            @PathVariable("id") String id,
            @PathVariable("leadId") String leadId,
            @Valid @RequestBody(required = false) ApproveLeadRequest req) {
        return ResponseEntity.ok(leadService.approve(
                id, leadId,
                req == null ? null : req.getConfirmedDatetime(),
                LeadDecisionChannel.DASHBOARD));
    }

    @PostMapping("/{leadId}/decline")
    @PreAuthorize("hasAuthority('SCOPE_leads.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadResponse> decline(
            @PathVariable("id") String id,
            @PathVariable("leadId") String leadId,
            @Valid @RequestBody DeclineLeadRequest req) {
        return ResponseEntity.ok(leadService.decline(
                id, leadId, req.getReason(), LeadDecisionChannel.DASHBOARD));
    }

    @PostMapping("/{leadId}/ignore")
    @PreAuthorize("hasAuthority('SCOPE_leads.write') and @businessAccessGuard.canAccess(#id)")
    public ResponseEntity<LeadResponse> ignore(
            @PathVariable("id") String id,
            @PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(leadService.ignore(id, leadId, LeadDecisionChannel.DASHBOARD));
    }
}
