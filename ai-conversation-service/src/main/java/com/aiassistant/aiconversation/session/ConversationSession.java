package com.aiassistant.aiconversation.session;

import com.aiassistant.aiconversation.llm.LlmMessage;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.TokenUsage;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One per WebSocket connection, keyed by {@code conversationId}.
 * Holds the rendered business {@code knowledge}, the running chat history,
 * and a small queue of {@link PendingMessage}s buffered while we are
 * waiting for {@code INIT} (knowledge-on-demand handshake).
 */
@Getter
public class ConversationSession {

    private final String conversationId;
    private final String businessId;
    private final LlmProvider provider;
    private final Instant createdAt;

    /** Rendered business knowledge. Nullable until INIT arrives. */
    @Setter private volatile String knowledge;

    private final List<LlmMessage> messages = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<TokenUsage> usage = new AtomicReference<>(TokenUsage.zero());

    /** User messages received before knowledge was available. Replayed in order on INIT. */
    private final Deque<PendingMessage> pendingMessages = new ArrayDeque<>();

    @Setter private volatile Instant lastActivityAt;

    /** Consecutive UNCLEAR_MESSAGE turns — reset by a normal MESSAGE.
     *  Used to escalate to CALLBACK_NEEDED after N unclear turns in a row. */
    private final AtomicInteger unclearStreak = new AtomicInteger();

    /** Consecutive SILENCE_PROMPT turns — reset by a normal MESSAGE.
     *  Used to escalate to HANGUP after N prompts go unanswered. */
    private final AtomicInteger silenceStreak = new AtomicInteger();

    /** Customer language, supplied via INIT metadata. Used to pick the right
     *  canned text for UNCLEAR / SILENCE / greeting paths without an LLM hop. */
    @Setter private volatile String language;

    public ConversationSession(String conversationId, String businessId,
                               LlmProvider provider, String knowledge) {
        this.conversationId = conversationId;
        this.businessId = businessId;
        this.provider = provider;
        this.knowledge = knowledge;
        this.createdAt = Instant.now();
        this.lastActivityAt = this.createdAt;
    }

    public boolean hasKnowledge() {
        return knowledge != null && !knowledge.isBlank();
    }

    public void appendUser(String text) {
        messages.add(LlmMessage.builder().role(LlmMessage.Role.USER).content(text).build());
        touch();
    }

    public void appendAssistant(String text) {
        messages.add(LlmMessage.builder().role(LlmMessage.Role.ASSISTANT).content(text).build());
        touch();
    }

    public int incrementUnclearStreak() {
        touch();
        return unclearStreak.incrementAndGet();
    }

    public void resetUnclearStreak() {
        unclearStreak.set(0);
    }

    public int incrementSilenceStreak() {
        touch();
        return silenceStreak.incrementAndGet();
    }

    public void resetSilenceStreak() {
        silenceStreak.set(0);
    }

    public void addUsage(TokenUsage delta) {
        if (delta == null) return;
        usage.updateAndGet(u -> u.add(delta));
    }

    public List<LlmMessage> snapshotMessages(int historyWindowTurns) {
        synchronized (messages) {
            int max = historyWindowTurns > 0 ? historyWindowTurns * 2 : messages.size();
            int from = Math.max(0, messages.size() - max);
            return List.copyOf(messages.subList(from, messages.size()));
        }
    }

    public synchronized void queuePending(String messageId, String text) {
        pendingMessages.addLast(new PendingMessage(messageId, text));
        touch();
    }

    public synchronized List<PendingMessage> drainPending() {
        if (pendingMessages.isEmpty()) return List.of();
        List<PendingMessage> drained = new ArrayList<>(pendingMessages);
        pendingMessages.clear();
        return drained;
    }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public record PendingMessage(String messageId, String text) {}
}