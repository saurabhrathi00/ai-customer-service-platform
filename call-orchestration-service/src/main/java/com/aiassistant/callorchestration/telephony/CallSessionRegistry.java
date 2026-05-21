package com.aiassistant.callorchestration.telephony;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class CallSessionRegistry {

    private final ConcurrentMap<String, CallSession> sessions = new ConcurrentHashMap<>();

    public void put(CallSession session) {
        sessions.put(session.getCallId(), session);
    }

    public Optional<CallSession> get(String callId) {
        return Optional.ofNullable(sessions.get(callId));
    }

    /**
     * Look up a session by its conversationId (the ai-conversation-service
     * identifier, distinct from the telephony callId). Linear scan — fine
     * for MVP traffic; if active sessions grow, replace with a secondary
     * {@code ConcurrentMap<conversationId, callId>} index.
     */
    public Optional<CallSession> findByConversationId(String conversationId) {
        if (conversationId == null) return Optional.empty();
        return sessions.values().stream()
                .filter(s -> conversationId.equals(s.getConversationId()))
                .findFirst();
    }

    public Optional<CallSession> remove(String callId) {
        return Optional.ofNullable(sessions.remove(callId));
    }

    public int size() {
        return sessions.size();
    }
}
