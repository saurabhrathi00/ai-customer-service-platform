package com.aiassistant.callorchestration.services;

import com.aiassistant.callorchestration.clients.ConversationSummaryServiceClient;
import com.aiassistant.callorchestration.clients.NotificationServiceClient;
import com.aiassistant.callorchestration.models.dao.CallLogEntity;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.CallSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PostCallOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PostCallOrchestrator.class);

    private final CallLogService callLogService;
    private final CallSessionRegistry callSessionRegistry;
    private final ConversationSummaryServiceClient summaryClient;
    private final NotificationServiceClient notificationClient;

    /**
     * Single post-call entry point used by both scenarios:
     * <ol>
     *   <li>Normal end — {@code HISTORY} frame from ai-conversation-service.</li>
     *   <li>Abrupt end — {@code PUT /api/internal/calls/{conversationId}/history}.</li>
     * </ol>
     *
     * <p>Idempotent. The first invocation wins via
     * {@link CallSession#getFinalized()}; subsequent calls return {@code null}.
     */
    public CallLogEntity finalizeCall(CallSession session, List<Map<String, String>> history) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (!session.getFinalized().compareAndSet(false, true)) {
            log.info("finalizeCall skipped — already finalized callId={} conversationId={}",
                    session.getCallId(), session.getConversationId());
            return null;
        }
        if (history != null) {
            session.setHistory(history);
        }
        Instant endedAt = Instant.now();
        CallLogEntity callLog = callLogService.persistOnDisconnect(session, endedAt);
        log.info("Call finalized callId={} conversationId={} callLogId={}",
                session.getCallId(), session.getConversationId(), callLog.getId());

        triggerSummary(session, callLog);
        triggerCallbackNotification(callLog);

        callSessionRegistry.remove(session.getCallId());
        return callLog;
    }

    /**
     * Fire a lightweight trigger to conversation-summary-service. The
     * summary service acknowledges with 202, then fetches the transcript
     * back via {@code GET /api/internal/calls/{callLogId}/transcript},
     * runs the LLM, and writes its own {@code call_summaries} row. We do
     * NOT block on the summary itself — this hop is just "go do it".
     */
    @Async("postCallExecutor")
    public void triggerSummary(CallSession session, CallLogEntity callLog) {
        try {
            log.info("Triggering async summary for callLogId={}", callLog.getId());
            summaryClient.trigger(callLog.getId());
        } catch (Exception ex) {
            log.error("Failed to trigger summary for callLogId={}: {}",
                    callLog.getId(), ex.getMessage(), ex);
        }
    }

    @Async("postCallExecutor")
    public void triggerCallbackNotification(CallLogEntity callLog) {
        if (!Boolean.TRUE.equals(callLog.getCallbackRequested())) return;
        try {
            log.info("Triggering callback notification for callId={}", callLog.getId());
            notificationClient.notifyCallback(callLog.getBusinessId(), callLog.getCustomerPhone(),
                    callLog.getCallSummary());
        } catch (Exception ex) {
            log.error("Failed to send callback notification callId={}: {}", callLog.getId(), ex.getMessage(), ex);
        }
    }
}
