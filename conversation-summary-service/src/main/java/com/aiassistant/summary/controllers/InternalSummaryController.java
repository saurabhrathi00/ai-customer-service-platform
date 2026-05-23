package com.aiassistant.summary.controllers;

import com.aiassistant.summary.models.request.TriggerSummaryRequest;
import com.aiassistant.summary.services.SummaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fire-and-forget trigger from call-orchestration-service. We acknowledge
 * with 202 immediately; the LLM call + DB write happen on a background
 * pool. Caller does not learn the result on this hop.
 */
@RestController
@RequestMapping("/api/internal/summary")
@RequiredArgsConstructor
public class InternalSummaryController {

    private final SummaryService summaryService;

    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('SCOPE_summary.internal.invoke')")
    public ResponseEntity<Map<String, String>> trigger(@Valid @RequestBody TriggerSummaryRequest req) {
        summaryService.scheduleSummary(req.getCallLogId());
        return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "callLogId", req.getCallLogId()));
    }
}
