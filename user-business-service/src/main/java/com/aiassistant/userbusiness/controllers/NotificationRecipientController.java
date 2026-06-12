package com.aiassistant.userbusiness.controllers;

import com.aiassistant.userbusiness.models.request.AddNotificationRecipientRequest;
import com.aiassistant.userbusiness.models.response.NotificationRecipientResponse;
import com.aiassistant.userbusiness.services.NotificationRecipientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/{id}/notification-recipients")
@RequiredArgsConstructor
public class NotificationRecipientController {

    private final NotificationRecipientService service;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_business.read') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<List<NotificationRecipientResponse>> list(@PathVariable("id") String businessId) {
        return ResponseEntity.ok(service.list(businessId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<NotificationRecipientResponse> add(@PathVariable("id") String businessId,
                                                             @Valid @RequestBody AddNotificationRecipientRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.add(businessId, req));
    }

    @DeleteMapping("/{recipientId}")
    @PreAuthorize("hasAuthority('SCOPE_business.write') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<Void> delete(@PathVariable("id") String businessId,
                                       @PathVariable("recipientId") String recipientId) {
        service.delete(businessId, recipientId);
        return ResponseEntity.noContent().build();
    }
}
