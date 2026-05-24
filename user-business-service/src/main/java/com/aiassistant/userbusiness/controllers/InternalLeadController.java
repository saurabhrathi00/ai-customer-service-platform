package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.enums.LeadDecisionChannel;
import com.aiassistant.userbusiness.models.request.ApproveLeadRequest;
import com.aiassistant.userbusiness.models.request.CreateLeadRequest;
import com.aiassistant.userbusiness.models.request.DeclineLeadRequest;
import com.aiassistant.userbusiness.models.response.LeadResponse;
import com.aiassistant.userbusiness.services.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Service-to-service endpoints used by:
 * <ul>
 *   <li>{@code conversation-summary-service} → POST a new lead after the
 *       post-call summary detects a trigger.</li>
 *   <li>{@code notification-service} → poll due reminders, mark reminders
 *       sent, finalise leads acted on via the WhatsApp deep-link.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/internal/leads")
@RequiredArgsConstructor
public class InternalLeadController {

    private final LeadService leadService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody CreateLeadRequest req) {
        // Below-threshold candidates produce empty — we return 204 so summary-service
        // can tell "dropped" apart from "persisted" without inspecting the body.
        return leadService.createFromCall(req)
                .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/due-reminders")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.read')")
    public ResponseEntity<List<LeadResponse>> dueReminders(
            @RequestParam(value = "cutoff", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cutoff) {
        return ResponseEntity.ok(leadService.listDueReminders(cutoff == null ? Instant.now() : cutoff));
    }

    @PostMapping("/{leadId}/reminder-sent")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> reminderSent(@PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(leadService.recordReminderSent(leadId));
    }

    @GetMapping("/pending-owner-notifications")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.read')")
    public ResponseEntity<List<LeadResponse>> pendingOwnerNotifications() {
        return ResponseEntity.ok(leadService.listPendingOwnerNotifications());
    }

    @GetMapping("/pending-customer-notifications")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.read')")
    public ResponseEntity<List<LeadResponse>> pendingCustomerNotifications() {
        return ResponseEntity.ok(leadService.listPendingCustomerNotifications());
    }

    @PostMapping("/{leadId}/owner-notified")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> ownerNotified(@PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(leadService.recordOwnerNotified(leadId));
    }

    @PostMapping("/{leadId}/customer-notified")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> customerNotified(@PathVariable("leadId") String leadId) {
        return ResponseEntity.ok(leadService.recordCustomerNotified(leadId));
    }

    @PostMapping("/{leadId}/approve-via-whatsapp")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> approveViaWhatsapp(
            @PathVariable("leadId") String leadId,
            @RequestParam("businessId") String businessId,
            @Valid @RequestBody(required = false) ApproveLeadRequest req) {
        return ResponseEntity.ok(leadService.approve(
                businessId, leadId,
                req == null ? null : req.getConfirmedDatetime(),
                LeadDecisionChannel.WHATSAPP));
    }

    @PostMapping("/{leadId}/decline-via-whatsapp")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> declineViaWhatsapp(
            @PathVariable("leadId") String leadId,
            @RequestParam("businessId") String businessId,
            @Valid @RequestBody DeclineLeadRequest req) {
        return ResponseEntity.ok(leadService.decline(
                businessId, leadId, req.getReason(), LeadDecisionChannel.WHATSAPP));
    }

    @PostMapping("/{leadId}/ignore-via-whatsapp")
    @PreAuthorize("hasAuthority('SCOPE_leads.internal.write')")
    public ResponseEntity<LeadResponse> ignoreViaWhatsapp(
            @PathVariable("leadId") String leadId,
            @RequestParam("businessId") String businessId) {
        return ResponseEntity.ok(leadService.ignore(businessId, leadId, LeadDecisionChannel.WHATSAPP));
    }
}
