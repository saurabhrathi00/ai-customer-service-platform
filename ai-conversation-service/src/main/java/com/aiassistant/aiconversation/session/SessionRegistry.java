package com.aiassistant.aiconversation.session;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.exceptions.AppException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    private final ServiceConfiguration serviceConfiguration;
    private final ConcurrentHashMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    private Consumer<ConversationSession> idleHandler;

    public void setIdleHandler(Consumer<ConversationSession> handler) {
        this.idleHandler = handler;
    }

    public void register(ConversationSession session) {
        int max = serviceConfiguration.getSession().getMaxConcurrent();
        if (max > 0 && sessions.size() >= max) {
            throw new AppException("Max concurrent sessions reached: " + max);
        }
        if (sessions.putIfAbsent(session.getConversationId(), session) != null) {
            throw new AppException("Session already exists: " + session.getConversationId());
        }
    }

    public ConversationSession get(String conversationId) {
        return sessions.get(conversationId);
    }

    public ConversationSession remove(String conversationId) {
        return sessions.remove(conversationId);
    }

    public Collection<ConversationSession> all() {
        return sessions.values();
    }

    public int size() {
        return sessions.size();
    }

    @Scheduled(fixedDelayString = "${configs.session.sweepIntervalSeconds:60}000")
    public void sweepIdle() {
        int idleMinutes = serviceConfiguration.getSession().getIdleTimeoutMinutes();
        if (idleMinutes <= 0) return;
        Instant threshold = Instant.now().minus(Duration.ofMinutes(idleMinutes));
        sessions.values().forEach(s -> {
            if (s.getLastActivityAt().isBefore(threshold)) {
                ConversationSession removed = sessions.remove(s.getConversationId());
                if (removed != null) {
                    log.info("Sweeping idle session conversationId={}", removed.getConversationId());
                    if (idleHandler != null) {
                        try { idleHandler.accept(removed); }
                        catch (Exception e) { log.warn("idleHandler failed: {}", e.getMessage()); }
                    }
                }
            }
        });
    }
}