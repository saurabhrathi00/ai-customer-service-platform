package com.aiassistant.callorchestration.clients.ws;

import java.util.List;
import java.util.Map;

/**
 * Per-conversation event handler that the WS client invokes for each
 * inbound frame from ai-conversation-service. Implementations live in
 * {@code services/ConversationCoordinator}.
 */
public interface AiConversationCallbacks {

    /** AI replied to a customer message. {@code replyToMessageId} matches the MESSAGE id we sent. */
    void onResponse(String conversationId, String replyToMessageId, String text);

    /** ai-conv is missing knowledge — we must send another INIT with the rendered knowledge. */
    void onKnowledgeRequest(String conversationId);

    /** AI could not answer from the supplied knowledge — trigger the callback flow. */
    void onCallbackNeeded(String conversationId, String replyToMessageId);

    /**
     * Final frame from ai-conversation-service after we send {@code END}.
     * Carries the full conversation history; the implementation should stash
     * it on {@code CallSession} and trigger the post-call persistence flow.
     */
    void onHistory(String conversationId, List<Map<String, String>> history);

    /** Non-fatal protocol/LLM error frame. */
    void onError(String conversationId, String code, String message);

    /** WS closed (cleanly or otherwise). */
    void onClosed(String conversationId, int closeCode, String reason);
}