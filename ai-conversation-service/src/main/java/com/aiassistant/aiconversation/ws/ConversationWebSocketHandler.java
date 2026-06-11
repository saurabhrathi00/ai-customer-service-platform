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
    private final com.aiassistant.aiconversation.services.ConversationHistoryService historyService;
    private final com.aiassistant.aiconversation.llm.gemini.GeminiContextCacheService geminiContextCacheService;

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
            case BARGE_IN -> handleBargeIn(ws, frame);
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
            log.debug("[unclear] conversationId={} streak={} action=HANGUP reply=\"{}\"",
                    conversationId, streak, UNCLEAR_GIVEUP);
            send(ws, OutboundFrames.Hangup.builder()
                    .conversationId(conversationId)
                    .replyToMessageId(frame.getMessageId())
                    .text(UNCLEAR_GIVEUP)
                    .reason("UNCLEAR")
                    .build());
            return;
        }
        log.debug("[unclear] conversationId={} streak={} action=ASK_REPEAT reply=\"{}\"",
                conversationId, streak, UNCLEAR_REPLY);
        send(ws, OutboundFrames.Response.builder()
                .conversationId(conversationId)
                .replyToMessageId(frame.getMessageId())
                .text(UNCLEAR_REPLY)
                .build());
    }

    // ─── BARGE_IN ────────────────────────────────────────────────────────

    /**
     * Caller cut in while the bot was speaking. Cancel the in-flight LLM
     * subscription so Gemini stops generating tokens for an abandoned
     * question, and clear the active-turn marker so any tail deltas that
     * arrive between dispose() and the upstream HTTP close are treated as
     * superseded and skipped (no append to history).
     */
    private void handleBargeIn(WebSocketSession ws, InboundFrame frame) {
        String conversationId = resolveConversationId(ws, frame);
        if (conversationId == null) return;
        ConversationSession session = sessionRegistry.get(conversationId);
        if (session == null) return;

        reactor.core.Disposable prev = session.getCurrentTurn().getAndSet(null);
        // Null out the active turn id so the dispose-vs-finally race doesn't
        // append a half-formed reply to history.
        session.setCurrentTurnMessageId(null);
        if (prev != null && !prev.isDisposed()) {
            log.debug("[barge-in] cancelling in-flight turn conversationId={}", conversationId);
            prev.dispose();
        } else {
            log.debug("[barge-in] no in-flight turn to cancel conversationId={}", conversationId);
        }
        // Drop the unanswered user message from history. Without this the
        // model sees a stale question on the next turn and tries to answer
        // it on top of the new one — bot ends up "still answering the old
        // question" even after the caller has moved on.
        if (session.popLastIfUser()) {
            log.debug("[barge-in] popped unanswered user message conversationId={}", conversationId);
        }
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

        String text = frame.getText();
        if (frame.isLowConfidence()) {
            log.info("[ws] low-confidence message conversationId={} text=\"{}\"",
                    conversationId, text);
            text = "(Note: audio was unclear, transcription may be inaccurate) " + text;
        }
        processTurn(ws, session, frame.getMessageId(), text);
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
        long turnStart = System.currentTimeMillis();
        log.debug("[latency] conversationId={} stage=turn-start chars={}",
                session.getConversationId(), userText == null ? 0 : userText.length());

        // Cancel any previous in-flight turn for this session. Disposing the
        // Reactor subscription propagates cancellation upstream so the Gemini
        // HTTP stream stops generating tokens for the abandoned question.
        reactor.core.Disposable prev = session.getCurrentTurn().getAndSet(null);
        if (prev != null && !prev.isDisposed()) {
            log.debug("[turn-cancel] conversationId={} superseded by replyTo={}",
                    session.getConversationId(), replyToMessageId);
            prev.dispose();
        }
        session.setCurrentTurnMessageId(replyToMessageId);

        session.appendUser(userText);
        session.resetUnclearStreak();

        ServiceConfiguration.Llm cfg = serviceConfiguration.getLlm();
        String renderedPrompt = promptBuilder.build(session.getKnowledge());
        String cachedContentName = null;
        if (cfg.isPromptCacheEnabled() && "gemini".equals(session.getProvider().id())) {
            cachedContentName = geminiContextCacheService.resolveCache(
                    session.getBusinessId(), renderedPrompt);
        }
        LlmRequest req = LlmRequest.builder()
                .systemPrompt(cachedContentName != null ? null : renderedPrompt)
                .cachedContentName(cachedContentName)
                .messages(session.snapshotMessages(cfg.getHistoryWindowTurns()))
                .maxOutputTokens(cfg.getMaxOutputTokens())
                .temperature(cfg.getTemperature())
                .cacheSystemPrompt(cfg.isPromptCacheEnabled())
                .build();
        long llmStart = System.currentTimeMillis();
        log.debug("[latency] conversationId={} stage=prompt-built ms={} messages={}",
                session.getConversationId(), llmStart - turnStart,
                req.getMessages() == null ? 0 : req.getMessages().size());

        TurnState state = new TurnState(replyToMessageId);
        long[] firstDeltaAt = {-1};

        // Async, cancellable subscription. The Tomcat NIO thread returns
        // immediately so the next inbound MESSAGE can be read and supersede
        // this turn if the caller starts speaking again.
        reactor.core.Disposable sub = reactor.core.publisher.Flux
                .from(session.getProvider().streamReply(req))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe(
                        delta -> {
                            if (isSuperseded(session, replyToMessageId)) return;
                            if (firstDeltaAt[0] < 0) {
                                firstDeltaAt[0] = System.currentTimeMillis();
                                log.debug("[latency] conversationId={} stage=llm-first-token ms={}",
                                        session.getConversationId(), firstDeltaAt[0] - llmStart);
                            }
                            emitDelta(ws, session, state, delta);
                        },
                        err -> {
                            if (isSuperseded(session, replyToMessageId)) return;
                            if (err instanceof LlmException le) {
                                log.warn("LLM stream failed conversationId={} code={} msg={}",
                                        session.getConversationId(), le.getCode(), le.getMessage());
                                sendErrorQuiet(ws, session.getConversationId(), le.getCode(), le.getMessage());
                            } else {
                                log.warn("LLM stream failed conversationId={}: {}",
                                        session.getConversationId(), err.getMessage());
                                sendErrorQuiet(ws, session.getConversationId(), "LLM_TRANSIENT", err.getMessage());
                            }
                        },
                        () -> {
                            if (isSuperseded(session, replyToMessageId)) {
                                log.debug("[turn-cancel] conversationId={} discarding reply for superseded replyTo={}",
                                        session.getConversationId(), replyToMessageId);
                                return;
                            }
                            log.debug("[latency] conversationId={} stage=llm-total ms={} firstTokenMs={}",
                                    session.getConversationId(),
                                    System.currentTimeMillis() - llmStart,
                                    firstDeltaAt[0] < 0 ? -1 : firstDeltaAt[0] - llmStart);
                            try { finishTurn(ws, session, state); }
                            catch (IOException io) {
                                log.warn("finishTurn failed conversationId={}: {}",
                                        session.getConversationId(), io.getMessage());
                            }
                        }
                );

        session.getCurrentTurn().set(sub);
    }

    /** A turn is superseded once a newer MESSAGE has overwritten the session's
     *  currentTurnMessageId. Superseded turns must not emit deltas, must not
     *  finalize, and must not append to history — otherwise the model's memory
     *  thinks it answered something the caller already moved past. */
    private boolean isSuperseded(ConversationSession session, String replyToMessageId) {
        String active = session.getCurrentTurnMessageId();
        return active != null && !active.equals(replyToMessageId);
    }

    /** Per-turn streaming state: accumulator, callback detection, last usage. */
    private static final class TurnState {
        final String replyToMessageId;
        final StringBuilder acc = new StringBuilder();
        /** Captures spoken text AFTER a mid-stream sentinel pipe. The model
         *  is supposed to emit "HANGUP|farewell" as the entire reply, but
         *  sometimes emits "ack text.HANGUP|farewell" instead — in that
         *  case we strip the leading ack from {@code acc} and route the
         *  trailing farewell text here so {@code finishTurn} can speak it. */
        final StringBuilder sentinelSpokenAcc = new StringBuilder();
        /** Sentinel state: UNKNOWN (still buffering), NORMAL, CALLBACK. */
        Mode mode = Mode.UNKNOWN;
        /** Once we know it's a normal reply, this is how much of {@code acc} we have already forwarded. */
        int forwardedUpto = 0;
        TokenUsage lastUsage;
        String finishReason;
        boolean doneEmitted = false;

        TurnState(String replyToMessageId) { this.replyToMessageId = replyToMessageId; }
    }

    private enum Mode { UNKNOWN, NORMAL, CALLBACK, HANGUP, DUPLICATE }

    /** Sentinels the LLM may emit at the very start of a reply. The longest
     *  one bounds how many chars we must buffer before classifying. The
     *  {@code DUPLICATE} sentinel has no trailing pipe — it's the entire
     *  output when the model decides the caller re-asked something already
     *  answered. */
    private static final String[] SENTINELS = {
            SystemPromptBuilder.CALLBACK_NEEDED + "|",
            SystemPromptBuilder.HANGUP + "|",
            SystemPromptBuilder.DUPLICATE
    };
    private static final int MAX_SENTINEL_LEN = Math.max(Math.max(
            SystemPromptBuilder.CALLBACK_NEEDED.length() + 1,
            SystemPromptBuilder.HANGUP.length() + 1),
            SystemPromptBuilder.DUPLICATE.length());

    private void emitDelta(WebSocketSession ws, ConversationSession session,
                           TurnState state, LlmDelta delta) {
        if (delta.getUsage() != null) state.lastUsage = delta.getUsage();
        if (delta.getFinishReason() != null) state.finishReason = delta.getFinishReason();

        String chunk = delta.getText();
        if (chunk == null || chunk.isEmpty()) return;

        // Once a mid-stream sentinel has switched us to HANGUP/CALLBACK,
        // every subsequent token is the spoken farewell — keep it out of
        // the normal accumulator (which is already truncated) so nothing
        // else gets TTS'd.
        if (state.mode == Mode.HANGUP || state.mode == Mode.CALLBACK) {
            state.sentinelSpokenAcc.append(chunk);
            return;
        }

        state.acc.append(chunk);

        // Mid-stream sentinel guard. Model sometimes emits "ack.HANGUP|bye"
        // instead of just "HANGUP|bye" — without this check we'd happily
        // stream the literal word "HANGUP" to TTS.
        int hangIdx = state.acc.indexOf(SystemPromptBuilder.HANGUP + "|");
        int callIdx = state.acc.indexOf(SystemPromptBuilder.CALLBACK_NEEDED + "|");
        int idx; Mode newMode;
        if (hangIdx >= 0 && (callIdx < 0 || hangIdx < callIdx)) {
            idx = hangIdx; newMode = Mode.HANGUP;
        } else if (callIdx >= 0) {
            idx = callIdx; newMode = Mode.CALLBACK;
        } else {
            idx = -1; newMode = null;
        }

        if (idx >= 0 && (state.mode == Mode.NORMAL || state.mode == Mode.UNKNOWN)) {
            int pipeIdx = state.acc.indexOf("|", idx);
            String tail = (pipeIdx > 0 && pipeIdx + 1 < state.acc.length())
                    ? state.acc.substring(pipeIdx + 1) : "";
            state.sentinelSpokenAcc.append(tail);
            // Strip the sentinel + everything after from the live accumulator.
            // We deliberately also drop any unflushed pre-sentinel text — it
            // was the model's "preamble" that it then decided to follow with
            // the hangup, so speaking half of it would be incoherent. Already-
            // flushed prefix can't be unsent, but that's fine; the farewell
            // we play in HANGUP mode bridges naturally.
            state.acc.setLength(Math.min(state.forwardedUpto, idx));
            state.mode = newMode;
            log.debug("[sentinel] mid-stream {} detected conversationId={} idxInAcc={}",
                    newMode, session.getConversationId(), idx);
            return;
        }

        try {
            forwardOrBuffer(ws, session, state);
        } catch (IOException io) {
            throw new RuntimeException(io);
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
            if (head.startsWith(SystemPromptBuilder.DUPLICATE)) {
                state.mode = Mode.DUPLICATE;
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
        // CALLBACK / HANGUP / DUPLICATE: buffer silently until done.
    }

    private void flushNormalDeltas(WebSocketSession ws, ConversationSession session,
                                   TurnState state) throws IOException {
        flushNormalDeltas(ws, session, state, false);
    }

    /**
     * Emit accumulated text in sentence-shaped chunks. Break points are
     * sentence-terminal punctuation only: {@code . ! ? \n \r ।} — included
     * at the end of the chunk that precedes them.
     *
     * <p>Earlier this also broke at commas / colons / conjunctions to
     * minimise first-audio latency, but the downstream TTS (one HTTP
     * request per chunk, fired on a parallel executor in call-orch)
     * delivered chunks out of order and reset prosody every fragment —
     * making replies sound choppy. Sentence-level chunks are the sweet
     * spot: still streaming, but each piece is a self-contained prosodic
     * unit so playback is smooth.
     *
     * <p>Mid-stream we only emit up to the latest complete sentence
     * boundary so we never speak a half-word. On {@code finalFlush=true}
     * the tail is emitted regardless.
     */
    private void flushNormalDeltas(WebSocketSession ws, ConversationSession session,
                                   TurnState state, boolean finalFlush) throws IOException {
        int len = state.acc.length();
        if (state.forwardedUpto >= len) return;

        int emitUpto = finalFlush ? len : lastCompleteBoundary(state.acc, state.forwardedUpto, len);
        if (emitUpto <= state.forwardedUpto) return;

        String segment = state.acc.substring(state.forwardedUpto, emitUpto);
        state.forwardedUpto = emitUpto;

        for (String piece : splitIntoChunks(segment)) {
            String trimmed = stripNameTag(piece.strip());
            if (trimmed.isEmpty()) continue;
            send(ws, OutboundFrames.ResponseDelta.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .text(trimmed)
                    .build());
        }
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r' || c == '।';
    }

    /** Latest mid-stream sentence boundary in [from, to). Returns the
     *  exclusive end index of the chunk that can be safely emitted now,
     *  or {@code from} if none. */
    private static int lastCompleteBoundary(CharSequence s, int from, int to) {
        for (int i = to - 1; i >= from; i--) {
            if (isSentenceEnd(s.charAt(i))) return i + 1;
        }
        return from;
    }

    private static final java.util.regex.Pattern NAME_TAG =
            java.util.regex.Pattern.compile("\\{NAME\\}\\|[^{}]*$");

    private static String stripNameTag(String s) {
        return NAME_TAG.matcher(s).replaceAll("").strip();
    }

    private static String extractName(String s) {
        java.util.regex.Matcher m = NAME_TAG.matcher(s);
        if (m.find()) {
            String tag = m.group();
            int pipe = tag.indexOf('|');
            return pipe >= 0 && pipe + 1 < tag.length() ? tag.substring(pipe + 1).strip() : null;
        }
        return null;
    }

    private static java.util.List<String> splitIntoChunks(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int start = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (isSentenceEnd(s.charAt(i))) {
                out.add(s.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < len) out.add(s.substring(start));
        return out;
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
            String spoken = pickSpoken(state, full);
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
            String spoken = pickSpoken(state, full);
            if (!spoken.isEmpty()) session.appendAssistant(spoken);
            send(ws, OutboundFrames.Hangup.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .text(spoken.isEmpty() ? null : spoken)
                    .reason("GOODBYE")
                    .build());
            return;
        }
        // Duplicate detection — caller re-asked something already answered.
        // Suppress audio entirely (no text to call-orch) but emit a
        // RESPONSE_DONE with finishReason=DUPLICATE so call-orch's per-turn
        // bookkeeping closes cleanly. We also do NOT appendAssistant since
        // no spoken text was produced.
        if (state.mode == Mode.DUPLICATE
                || (state.mode == Mode.UNKNOWN && full.startsWith(SystemPromptBuilder.DUPLICATE))) {
            log.info("[duplicate] suppressed reply conversationId={} replyTo={} (caller re-asked)",
                    session.getConversationId(), state.replyToMessageId);
            send(ws, OutboundFrames.ResponseDone.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(state.replyToMessageId)
                    .finishReason("DUPLICATE")
                    .usage(state.lastUsage)
                    .build());
            return;
        }

        // Normal reply — make sure anything still buffered is forwarded.
        if (state.mode == Mode.UNKNOWN) state.mode = Mode.NORMAL;
        flushNormalDeltas(ws, session, state, true);
        String callerName = extractName(full);
        if (callerName != null && !callerName.isBlank()) {
            session.setCallerName(callerName);
            log.info("[name-capture] conversationId={} name={}", session.getConversationId(), callerName);
        }
        session.appendAssistant(stripNameTag(full));
        send(ws, OutboundFrames.ResponseDone.builder()
                .conversationId(session.getConversationId())
                .replyToMessageId(state.replyToMessageId)
                .finishReason(state.finishReason)
                .usage(state.lastUsage)
                .build());
    }

    /** Prefer the mid-stream sentinel buffer (set when HANGUP/CALLBACK
     *  appeared inside a normal-looking reply) over parsing the full text.
     *  Falls back to the classic {@link #extractSpoken} for the well-formed
     *  case where the sentinel was the very first token. */
    private static String pickSpoken(TurnState state, String full) {
        if (state.sentinelSpokenAcc.length() > 0) {
            return state.sentinelSpokenAcc.toString().trim();
        }
        return extractSpoken(full);
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
        if (conversationId == null) return;

        // Persist BEFORE we drop the session from the registry — once the
        // entry is gone we can't recover the message list to write.
        ConversationSession session = sessionRegistry.get(conversationId);
        if (session != null) {
            try { historyService.persistOnClose(session); }
            catch (Exception ex) {
                log.warn("history persistence failed conversationId={}: {}",
                        conversationId, ex.getMessage());
            }
        }
        sessionRegistry.remove(conversationId);
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