package com.aiassistant.aiconversation.services;

import com.aiassistant.aiconversation.llm.LlmMessage;
import com.aiassistant.aiconversation.llm.TokenUsage;
import com.aiassistant.aiconversation.models.dao.ConversationHistoryEntity;
import com.aiassistant.aiconversation.repository.ConversationHistoryRepository;
import com.aiassistant.aiconversation.session.ConversationSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persists the conversation snapshot when the WebSocket closes. Idempotent
 * via {@code conversation_id UNIQUE} + a pre-check, so a flapping client
 * that re-opens the same conversationId won't blow up.
 */
@Service
@RequiredArgsConstructor
public class ConversationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryService.class);

    private final ConversationHistoryRepository repository;

    @Transactional
    public void persistOnClose(ConversationSession session) {
        if (session == null) return;
        String convId = session.getConversationId();
        if (convId == null || convId.isBlank()) return;

        // Skip empty sessions — nothing useful to save.
        List<LlmMessage> snapshot = session.snapshotMessages(0);
        if (snapshot.isEmpty()) {
            log.info("[history] skip — empty session conversationId={}", convId);
            return;
        }

        if (repository.existsByConversationId(convId)) {
            log.info("[history] skip — already persisted conversationId={}", convId);
            return;
        }

        TokenUsage usage = session.getUsage().get();
        ConversationHistoryEntity entity = ConversationHistoryEntity.builder()
                .conversationId(convId)
                .businessId(session.getBusinessId())
                .language(session.getLanguage())
                .messageCount(snapshot.size())
                .messages(toJsonShape(snapshot))
                .inputTokens(usage == null ? null : usage.getInputTokens())
                .outputTokens(usage == null ? null : usage.getOutputTokens())
                .startedAt(session.getCreatedAt())
                .endedAt(Instant.now())
                .build();
        try {
            repository.save(entity);
            log.info("[history] persisted conversationId={} businessId={} turns={}",
                    convId, session.getBusinessId(), snapshot.size());
        } catch (Exception ex) {
            // A duplicate-key race is the only expected failure here — log and move on.
            log.warn("[history] persist failed conversationId={}: {}", convId, ex.getMessage());
        }
    }

    private static List<Map<String, String>> toJsonShape(List<LlmMessage> messages) {
        List<Map<String, String>> out = new ArrayList<>(messages.size());
        for (LlmMessage m : messages) {
            if (m == null || m.getContent() == null) continue;
            String role = m.getRole() == LlmMessage.Role.USER ? "user" : "assistant";
            out.add(Map.of("role", role, "content", m.getContent()));
        }
        return out;
    }
}
