package com.aiassistant.callorchestration.models.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Payload for {@code PUT /api/internal/calls/{conversationId}/history}.
 *
 * <p>Sent by ai-conversation-service when its WebSocket to this service drops
 * without a clean {@code END} (abrupt-end scenario). The history is the full
 * conversation, where each entry is {@code {"role":"user|assistant",
 * "content":"..."}}.
 */
@Data
public class UpdateHistoryRequest {

    @NotEmpty(message = "history must not be empty")
    private List<Map<String, String>> history;
}
