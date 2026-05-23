package com.aiassistant.callorchestration.controllers;

import com.aiassistant.callorchestration.exceptions.CallNotFoundException;
import com.aiassistant.callorchestration.models.dao.CallLogEntity;
import com.aiassistant.callorchestration.models.request.UpdateFeedbackRequest;
import com.aiassistant.callorchestration.models.request.UpdateHistoryRequest;
import com.aiassistant.callorchestration.models.request.UpdateSummaryRequest;
import com.aiassistant.callorchestration.models.response.CallLogResponse;
import com.aiassistant.callorchestration.models.response.CallbackResponse;
import com.aiassistant.callorchestration.models.response.TranscriptPayload;
import com.aiassistant.callorchestration.services.CallLogService;
import com.aiassistant.callorchestration.services.PostCallOrchestrator;
import com.aiassistant.callorchestration.services.mapper.CallLogMapper;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.CallSessionRegistry;
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

import java.util.List;

@RestController
@RequestMapping("/api/internal/calls")
@RequiredArgsConstructor
public class InternalCallController {

    private final CallLogService callLogService;
    private final CallSessionRegistry callSessionRegistry;
    private final PostCallOrchestrator postCallOrchestrator;

    /**
     * Fetch the persisted transcript + caller context for a call.
     * conversation-summary-service calls this on its async pipeline after
     * receiving a /trigger from this service.
     */
    @GetMapping("/{callLogId}/transcript")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.read')")
    public ResponseEntity<TranscriptPayload> transcript(@PathVariable("callLogId") String callLogId) {
        return ResponseEntity.ok(callLogService.getTranscript(callLogId));
    }

    @GetMapping("/{businessId}/recent")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.read')")
    public ResponseEntity<List<CallLogResponse>> recent(@PathVariable("businessId") String businessId) {
        return ResponseEntity.ok(callLogService.listRecent(businessId));
    }

    @GetMapping("/{businessId}/callbacks")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.read')")
    public ResponseEntity<List<CallbackResponse>> callbacks(@PathVariable("businessId") String businessId) {
        return ResponseEntity.ok(callLogService.listCallbacks(businessId));
    }

    @PutMapping("/{callId}/feedback")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.write')")
    public ResponseEntity<CallLogResponse> updateFeedback(@PathVariable("callId") String callId,
                                                          @RequestBody UpdateFeedbackRequest request) {
        return ResponseEntity.ok(callLogService.updateFeedback(callId, request));
    }

    @PutMapping("/{callId}/summary")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.write')")
    public ResponseEntity<CallLogResponse> updateSummary(@PathVariable("callId") String callId,
                                                         @RequestBody UpdateSummaryRequest request) {
        return ResponseEntity.ok(callLogService.updateSummary(callId, request));
    }

    /**
     * Abrupt-end callback from ai-conversation-service. When the WS to this
     * service drops without a clean END frame, ai-conv POSTs the history
     * here so we can still persist the call_log and fire post-call tasks.
     */
    @PutMapping("/{conversationId}/history")
    @PreAuthorize("hasAuthority('SCOPE_calls.internal.write')")
    public ResponseEntity<CallLogResponse> updateHistory(@PathVariable("conversationId") String conversationId,
                                                         @Valid @RequestBody UpdateHistoryRequest request) {
        CallSession session = callSessionRegistry.findByConversationId(conversationId)
                .orElseThrow(() -> new CallNotFoundException(
                        "No active CallSession for conversationId=" + conversationId));
        CallLogEntity callLog = postCallOrchestrator.finalizeCall(session, request.getHistory());
        if (callLog == null) {
            // Already finalized by the HISTORY WS frame; return the row we already wrote.
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(CallLogMapper.toResponse(callLog));
    }
}
