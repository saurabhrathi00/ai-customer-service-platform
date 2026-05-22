package com.aiassistant.aiconversation.ws;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.exceptions.LlmException;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.LlmProviderRegistry;
import com.aiassistant.aiconversation.llm.LlmDelta;
import com.aiassistant.aiconversation.llm.LlmRequest;
import com.aiassistant.aiconversation.llm.TokenUsage;
import com.aiassistant.aiconversation.session.ConversationSession;
import com.aiassistant.aiconversation.session.ConversationSession.PendingMessage;
import com.aiassistant.aiconversation.session.SessionRegistry;
import com.aiassistant.aiconversation.session.SystemPromptBuilder;
import com.aiassistant.aiconversation.ws.dto.InboundFrame;
import com.aiassistant.aiconversation.ws.dto.OutboundFrames;
import com.aiassistant.aiconversation.ws.dto.WsMessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * WebSocket handler for {@code /ws/conversation/{conversationId}}.
 *
 * <p>Protocol (see CLAUDE.md and {@link WsMessageType}):
 * <ul>
 *   <li>Inbound  — {@code INIT}, {@code MESSAGE}, {@code END}</li>
 *   <li>Outbound — {@code RESPONSE}, {@code KNOWLEDGE_REQUEST}, {@code CALLBACK_NEEDED}, {@code ERROR}</li>
 * </ul>
 *
 * <p>If a {@code MESSAGE} arrives before {@code INIT} (no knowledge yet),
 * the message is queued and a {@code KNOWLEDGE_REQUEST} is sent back to
 * call-orchestration. When the matching {@code INIT} arrives, the queued
 * messages are replayed in order.
 */
@Component
@RequiredArgsConstructor
public class ConversationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ConversationWebSocketHandler.class);

    private static final CloseStatus INVALID_INIT = new CloseStatus(4001, "invalid INIT");
    private static final CloseStatus ID_MISMATCH = new CloseStatus(4003, "id mismatch");

    private static final String ATTR_CONVERSATION_ID = "conversationId";
    static final String ATTR_PATH_CONVERSATION_ID = "pathConversationId";

    private final ObjectMapper mapper;
    private final SessionRegistry sessionRegistry;
    private final LlmProviderRegistry providerRegistry;
    private final ServiceConfiguration serviceConfiguration;
    private final SystemPromptBuilder promptBuilder;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession ws) {
        // intentionally quiet
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession ws, @NonNull TextMessage message) throws Exception {
        InboundFrame frame;
        try {
            frame = mapper.readValue(message.getPayload(), InboundFrame.class);
        } catch (Exception e) {
            sendError(ws, null, "BAD_FRAME", "Unparseable frame");
            return;
        }
        if (frame.getType() == null) {
            sendError(ws, null, "BAD_FRAME", "Missing type");
            return;
        }
        switch (frame.getType()) {
            case INIT -> handleInit(ws, frame);
            case MESSAGE -> handleMessage(ws, frame);
            case UNCLEAR_MESSAGE -> handleUnclearMessage(ws, frame);
            case SILENCE_PROMPT -> handleSilencePrompt(ws, frame);
            case END -> ws.close(CloseStatus.NORMAL);
            default -> sendError(ws, frame.getConversationId(),
                    "BAD_FRAME", "Unsupported inbound type: " + frame.getType());
        }
    }

    // ─── INIT ────────────────────────────────────────────────────────────

    private void handleInit(WebSocketSession ws, InboundFrame frame) throws IOException {
        if (frame.getConversationId() == null || frame.getBusinessId() == null
                || frame.getKnowledge() == null || frame.getKnowledge().isBlank()) {
            ws.close(INVALID_INIT);
            return;
        }
        String pathId = (String) ws.getAttributes().get(ATTR_PATH_CONVERSATION_ID);
        if (pathId != null && !pathId.equals(frame.getConversationId())) {
            ws.close(ID_MISMATCH);
            return;
        }

        ConversationSession existing = sessionRegistry.get(frame.getConversationId());
        if (existing != null) {
            // INIT after a KNOWLEDGE_REQUEST — knowledge is being supplied late.
            existing.setKnowledge(frame.getKnowledge());
            replayPending(ws, existing);
            return;
        }

        LlmProvider provider;
        try {
            provider = providerRegistry.get(frame.getProvider());
        } catch (Exception e) {
            ws.close(new CloseStatus(4001, e.getMessage()));
            return;
        }

        ConversationSession session = new ConversationSession(
                frame.getConversationId(), frame.getBusinessId(), provider, frame.getKnowledge());

        try {
            sessionRegistry.register(session);
        } catch (Exception e) {
            sendError(ws, session.getConversationId(), "SESSION_REJECTED", e.getMessage());
            ws.close(CloseStatus.SERVER_ERROR);
            return;
        }
        ws.getAttributes().put(ATTR_CONVERSATION_ID, session.getConversationId());

        if (frame.getMetadata() != null) {
            Object lang = frame.getMetadata().get("language");
            if (lang instanceof String s && !s.isBlank()) session.setLanguage(s);
        }

        if (frame.getGreeting() != null && !frame.getGreeting().isBlank()) {
            session.appendAssistant(frame.getGreeting().trim());
        }
    }

    // ─── UNCLEAR_MESSAGE ─────────────────────────────────────────────────

    private static final int MAX_CONSECUTIVE_UNCLEAR = 3;
    /** Tri-lingual canned ask — same phrasing every time, no LLM round-trip. */
    private static final String UNCLEAR_REPLY =
            "Sorry, I couldn't catch that — kya aap dobara bol sakte hain?";
    private static final String UNCLEAR_GIVEUP =
            "Lagta hai line saaf nahi hai. Hum aapko thodi der mein call back kar denge.";

    private void handleUnclearMessage(WebSocketSession ws, InboundFrame frame) throws IOException {
        String conversationId = resolveConversationId(ws, frame);
        if (conversationId == null) {
            sendError(ws, null, "BAD_FRAME", "UNCLEAR_MESSAGE missing conversationId");
            return;
        }
        ConversationSession session = sessionRegistry.get(conversationId);
        int streak = session == null ? 1 : session.incrementUnclearStreak();

        if (streak >= MAX_CONSECUTIVE_UNCLEAR) {
            if (session != null) session.resetUnclearStreak();
            log.info("[unclear] conversationId={} streak={} action=HANGUP reply=\"{}\"",
                    conversationId, streak, UNCLEAR_GIVEUP);
            send(ws, OutboundFrames.Hangup.builder()
                    .conversationId(conversationId)
                    .replyToMessageId(frame.getMessageId())
                    .text(UNCLEAR_GIVEUP)
                    .reason("UNCLEAR")
                    .build());
            return;
        }
        log.info("[unclear] conversationId={} streak={} action=ASK_REPEAT reply=\"{}\"",
                conversationId, streak, UNCLEAR_REPLY);
        send(ws, OutboundFrames.Response.builder()
                .conversationId(conversationId)
                .replyToMessageId(frame.getMessageId())
                .text(UNCLEAR_REPLY)
                .build());
    }

    // ─── SILENCE_PROMPT ──────────────────────────────────────────────────

    private static final int MAX_SILENCE_PROMPTS = 3;

    private void handleSilencePrompt(WebSocketSession ws, InboundFrame frame) throws IOException {
        String conversationId = resolveConversationId(ws, frame);
        if (conversationId == null) {
            sendError(ws, null, "BAD_FRAME", "SILENCE_PROMPT missing conversationId");
            return;
        }
        ConversationSession session = sessionRegistry.get(conversationId);
        int count = session == null ? 1 : session.incrementSilenceStreak();
        String lang = session == null ? null : session.getLanguage();

        if (count >= MAX_SILENCE_PROMPTS) {
            if (session != null) session.resetSilenceStreak();
            String farewell = silenceFarewell(lang);
            log.info("[silence] conversationId={} count={} action=HANGUP reply=\"{}\"",
                    conversationId, count, farewell);
            send(ws, OutboundFrames.Hangup.builder()
                    .conversationId(conversationId)
                    .replyToMessageId(frame.getMessageId())
                    .text(farewell)
                    .reason("SILENCE")
                    .build());
            return;
        }
        String prompt = silencePrompt(lang, count);
        log.info("[silence] conversationId={} count={} action=PROMPT reply=\"{}\"",
                conversationId, count, prompt);
        send(ws, OutboundFrames.Response.builder()
                .conversationId(conversationId)
                .replyToMessageId(frame.getMessageId())
                .text(prompt)
                .build());
    }

    private static String silencePrompt(String language, int count) {
        boolean english = language != null && language.toLowerCase().startsWith("en");
        boolean hindi   = language != null && language.toLowerCase().startsWith("hi");
        if (english) {
            return count == 1
                    ? "Hello, are you still there? How can I help you?"
                    : "Hello? Can you hear me?";
        }
        if (hindi) {
            return count == 1
                    ? "Hello, kya aap line par hain? Main aapki kaise madad kar sakti hoon?"
                    : "Hello, aapki awaaz aa rahi hai?";
        }
        // Unknown — bilingual.
        return count == 1
                ? "Hello, kya aap line par hain? Are you still there?"
                : "Hello, can you hear me? Aapki awaaz aa rahi hai?";
    }

    private static String silenceFarewell(String language) {
        boolean english = language != null && language.toLowerCase().startsWith("en");
        boolean hindi   = language != null && language.toLowerCase().startsWith("hi");
        if (english) {
            return "I'm not receiving any response from your side, so I'll disconnect the call. Thank you for calling.";
        }
        if (hindi) {
            return "Aapki taraf se koi awaaz nahi aa rahi, isliye main call disconnect kar rahi hoon. Dhanyavaad.";
        }
        return "Aapki taraf se koi awaaz nahi aa rahi, isliye main call disconnect kar rahi hoon. Dhanyavaad.";
    }

    // ─── MESSAGE ─────────────────────────────────────────────────────────

    private void handleMessage(WebSocketSession ws, InboundFrame frame) throws IOException {
        if (frame.getText() == null || frame.getText().isBlank()) {
            sendError(ws, frame.getConversationId(), "BAD_FRAME", "MESSAGE missing text");
            return;
        }
        String conversationId = resolveConversationId(ws, frame);
        if (conversationId == null) {
            sendError(ws, null, "BAD_FRAME", "MESSAGE missing conversationId");
            return;
        }
        // Bind the WS to its conversationId on first MESSAGE if INIT hasn't been seen yet.
        ws.getAttributes().putIfAbsent(ATTR_CONVERSATION_ID, conversationId);

        ConversationSession session = sessionRegistry.get(conversationId);

        // Knowledge-missing flow: queue and ask call-orch for INIT.
        if (session == null || !session.hasKnowledge()) {
            if (session != null) {
                session.queuePending(frame.getMessageId(), frame.getText());
            } else {
                // No session yet — create a placeholder so we can queue pending messages.
                LlmProvider provider;
                try {
                    provider = providerRegistry.get(frame.getProvider());
                } catch (Exception e) {
                    sendError(ws, conversationId, "PROVIDER_NOT_CONFIGURED", e.getMessage());
                    return;
                }
                ConversationSession placeholder = new ConversationSession(
                        conversationId, frame.getBusinessId(), provider, null);
                try {
                    sessionRegistry.register(placeholder);
                } catch (Exception e) {
                    sendError(ws, conversationId, "SESSION_REJECTED", e.getMessage());
                    return;
                }
                placeholder.queuePending(frame.getMessageId(), frame.getText());
            }
            send(ws, OutboundFrames.KnowledgeRequest.builder()
                    .conversationId(conversationId)
                    .reason("knowledge missing for this conversation")
                    .build());
            return;
        }

        processTurn(ws, session, frame.getMessageId(), frame.getText());
    }

    private void replayPending(WebSocketSession ws, ConversationSession session) {
        for (PendingMessage pm : session.drainPending()) {
            try {
                processTurn(ws, session, pm.messageId(), pm.text());
            } catch (Exception e) {
                log.warn("Replay failed conversationId={} msg={}: {}",
                        session.getConversationId(), pm.messageId(), e.getMessage());
                try {
                    sendError(ws, session.getConversationId(), "REPLAY_FAILED", e.getMessage());
                } catch (IOException ignored) {}
            }
        }
    }

    // ─── LLM turn ────────────────────────────────────────────────────────

    private void processTurn(WebSocketSession ws, ConversationSession session,
                             String replyToMessageId, String userText) throws IOException {
        session.appendUser(userText);
        session.resetUnclearStreak();
        session.resetSilenceStreak();

        ServiceConfiguration.Llm cfg = serviceConfiguration.getLlm();
        LlmRequest req = LlmRequest.builder()
                .systemPrompt(promptBuilder.build(session.getKnowledge()))
                .messages(session.snapshotMessages(cfg.getHistoryWindowTurns()))
                .maxOutputTokens(cfg.getMaxOutputTokens())
                .temperature(cfg.getTemperature())
                .cacheSystemPrompt(cfg.isPromptCacheEnabled())
                .build();

        // Streaming LLM call. We buffer the first ~20 chars of text so we can
        // detect the {@code CALLBACK_NEEDED} sentinel before forwarding any
        // delta to call-orch — the sentinel must never leak to TTS. Once we
        // know it isn't a callback, all subsequent text is forwarded as
        // RESPONSE_DELTA frames as soon as it arrives.
        TurnState state = new TurnState(replyToMessageId);

        try {
            reactor.core.publisher.Flux<LlmDelta> stream = reactor.core.publisher.Flux
                    .from(session.getProvider().streamReply(req));

            // Blocking subscribe — this handler thread waits on the stream and
            // pushes WS frames inline. Per-turn ordering is preserved.
            stream.toIterable().forEach(delta -> emitDelta(ws, session, state, delta));

        } catch (LlmException e) {
            log.warn("LLM stream failed conversationId={} code={} msg={}",
                    session.getConversationId(), e.getCode(), e.getMessage());
            sendErrorQuiet(ws, session.getConversationId(), e.getCode(), e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("LLM stream failed conversationId={}: {}", session.getConversationId(), e.getMessage());
            sendErrorQuiet(ws, session.getConversationId(), "LLM_TRANSIENT", e.getMessage());
            return;
        }

        finishTurn(ws, session, state);
    }

    /** Per-turn streaming state: accumulator, callback detection, last usage. */
    private static final class TurnState {
        final String replyToMessageId;
        final StringBuilder acc = new StringBuilder();
        /** Sentinel state: UNKNOWN (still buffering), NORMAL, CALLBACK. */
        Mode mode = Mode.UNKNOWN;
        /** Once we know it's a normal reply, this is how much of {@code acc} we have already forwarded. */
        int forwardedUpto = 0;
        TokenUsage lastUsage;
        String finishReason;
        boolean doneEmitted = false;

        TurnState(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }
    }

    private enum Mode { UNKNOWN, NORMAL, CALLBACK, HANGUP }

    /** Sentinels the LLM may emit at the very start of a reply. The longest
     *  one bounds how many chars we must buffer before classifying. */
    private static final String[] SENTINELS = {
            SystemPromptBuilder.CALLBACK_NEEDED + "|",
            SystemPromptBuilder.HANGUP + "|"
    };
    private static final int MAX_SENTINEL_LEN = Math.max(
            SystemPromptBuilder.CALLBACK_NEEDED.length() + 1,
            SystemPromptBuilder.HANGUP.length() + 1);

    private void emitDelta(WebSocketSession ws, ConversationSession session,
                           TurnState state, LlmDelta delta) {
        if (delta.getUsage() != null) state.lastUsage = delta.getUsage();
        if (delta.getFinishReason() != null) state.finishReason = delta.getFinishReason();

        String chunk = delta.getText();
        if (chunk != null && !chunk.isEmpty()) {
            state.acc.append(chunk);
            try {
                forwardOrBuffer(ws, session, state);
            } catch (IOException io) {
                throw new RuntimeException(io);
            }
        }
    }

    private void forwardOrBuffer(WebSocketSession ws, ConversationSession session,
                                 TurnState state) throws IOException {
        if (state.mode == Mode.UNKNOWN) {
            String head = state.acc.toString().stripLeading();
            // Try to classify. If head matches a sentinel prefix exactly,
            // route to that mode. If head can no longer extend to any
            // sentinel, route to NORMAL and flush. Else keep buffering.
            if (head.startsWith(SystemPromptBuilder.CALLBACK_NEEDED + "|")) {
                state.mode = Mode.CALLBACK;
                return;
            }
            if (head.startsWith(SystemPromptBuilder.HANGUP + "|")) {
                state.mode = Mode.HANGUP;
                return;
            }
            boolean stillPossible = false;
            for (String s : SENTINELS) {
                if (s.startsWith(head)) { stillPossible = true; break; }
            }
            if (stillPossible && head.length() < MAX_SENTINEL_LEN) return;

            // Not a sentinel — emit as normal text.
            state.mode = Mode.NORMAL;
            flushNormalDeltas(ws, session, state);
            return;
        }
        if (state.mode == Mode.NORMAL) {
            flushNormalDeltas(ws, session, state);
        }
        // CALLBACK / HANGUP: buffer silently until done.
    }

    private void flushNormalDeltas(WebSocketSession ws, ConversationSession session,
                                   TurnState state) throws IOException {
        if (state.forwardedUpto < state.acc.length()) {
            String chunk = state.acc.substring(state.forwardedUpto);
            state.forwardedUpto = state.acc.length();
            send(ws, OutboundFrames.ResponseDelta.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .text(chunk)
                    .build());
        }
    }

    private void finishTurn(WebSocketSession ws, ConversationSession session,
                            TurnState state) throws IOException {
        if (state.doneEmitted) return;
        state.doneEmitted = true;
        if (state.lastUsage != null) session.addUsage(state.lastUsage);

        String full = state.acc.toString().trim();

        // Sentinel branches — extract spoken text after the '|' separator and
        // emit the appropriate signalling frame instead of RESPONSE_DONE.
        if (state.mode == Mode.CALLBACK
                || (state.mode == Mode.UNKNOWN && full.startsWith(SystemPromptBuilder.CALLBACK_NEEDED + "|"))) {
            String spoken = extractSpoken(full);
            if (!spoken.isEmpty()) session.appendAssistant(spoken);
            send(ws, OutboundFrames.CallbackNeeded.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .text(spoken.isEmpty() ? null : spoken)
                    .usage(state.lastUsage)
                    .build());
            return;
        }
        if (state.mode == Mode.HANGUP
                || (state.mode == Mode.UNKNOWN && full.startsWith(SystemPromptBuilder.HANGUP + "|"))) {
            String spoken = extractSpoken(full);
            if (!spoken.isEmpty()) session.appendAssistant(spoken);
            send(ws, OutboundFrames.Hangup.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .text(spoken.isEmpty() ? null : spoken)
                    .reason("GOODBYE")
                    .build());
            return;
        }

        // Normal reply — make sure anything still buffered is forwarded.
        if (state.mode == Mode.UNKNOWN) state.mode = Mode.NORMAL;
        flushNormalDeltas(ws, session, state);
        session.appendAssistant(full);
        send(ws, OutboundFrames.ResponseDone.builder()
                .conversationId(session.getConversationId())
                .replyToMessageId(state.replyToMessageId)
                .finishReason(state.finishReason)
                .usage(state.lastUsage)
                .build());
    }

    private static String extractSpoken(String full) {
        int pipe = full.indexOf('|');
        if (pipe >= 0 && pipe + 1 < full.length()) {
            return full.substring(pipe + 1).trim();
        }
        return "";
    }

    private void sendErrorQuiet(WebSocketSession ws, String conversationId,
                                String code, String message) {
        try { sendError(ws, conversationId, code, message); }
        catch (IOException ignored) {}
    }

    // ─── Lifecycle / utilities ───────────────────────────────────────────

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession ws, @NonNull CloseStatus status) {
        String conversationId = (String) ws.getAttributes().get(ATTR_CONVERSATION_ID);
        if (conversationId != null) {
            sessionRegistry.remove(conversationId);
        }
    }

    private String resolveConversationId(WebSocketSession ws, InboundFrame frame) {
        if (frame.getConversationId() != null && !frame.getConversationId().isBlank()) {
            return frame.getConversationId();
        }
        Object bound = ws.getAttributes().get(ATTR_CONVERSATION_ID);
        if (bound instanceof String s) return s;
        Object path = ws.getAttributes().get(ATTR_PATH_CONVERSATION_ID);
        return path instanceof String s ? s : null;
    }

    private void send(WebSocketSession ws, Object payload) throws IOException {
        if (!ws.isOpen()) return;
        synchronized (ws) {
            ws.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
    }

    private void sendError(WebSocketSession ws, String conversationId,
                           String code, String message) throws IOException {
        send(ws, OutboundFrames.Error.builder()
                .conversationId(conversationId)
                .code(code)
                .message(message)
                .build());
    }
}