package com.aiassistant.callorchestration.controllers;

import com.aiassistant.callorchestration.models.response.CallLogResponse;
import com.aiassistant.callorchestration.services.CallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallLogService callLogService;

    @GetMapping("/{businessId}/recent")
    @PreAuthorize("hasAuthority('SCOPE_calls.read') and @businessAccessGuard.canAccess(#businessId)")
    public ResponseEntity<List<CallLogResponse>> recent(@PathVariable("businessId") String businessId) {
        return ResponseEntity.ok(callLogService.listRecent(businessId));
    }
}
