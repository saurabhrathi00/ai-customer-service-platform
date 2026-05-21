package com.aiassistant.aiconversation.ws;

import com.aiassistant.aiconversation.configuration.ServiceConfiguration;
import com.aiassistant.aiconversation.exceptions.LlmException;
import com.aiassistant.aiconversation.llm.LlmProvider;
import com.aiassistant.aiconversation.llm.LlmProviderRegistry;
import com.aiassistant.aiconversation.llm.LlmReply;
import com.aiassistant.aiconversation.llm.LlmRequest;
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
        log.info("WS connected id={} remote={}", ws.getId(), ws.getRemoteAddress());
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
            log.info("Knowledge supplied late for conversationId={}", existing.getConversationId());
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

        log.info("Session created conversationId={} businessId={} provider={}",
                session.getConversationId(), session.getBusinessId(), provider.id());
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

        ServiceConfiguration.Llm cfg = serviceConfiguration.getLlm();
        LlmRequest req = LlmRequest.builder()
                .systemPrompt(promptBuilder.build(session.getKnowledge()))
                .messages(session.snapshotMessages(cfg.getHistoryWindowTurns()))
                .maxOutputTokens(cfg.getMaxOutputTokens())
                .temperature(cfg.getTemperature())
                .cacheSystemPrompt(cfg.isPromptCacheEnabled())
                .build();

        LlmReply reply;
        try {
            reply = session.getProvider().complete(req);
        } catch (LlmException e) {
            log.warn("LLM call failed conversationId={} code={} msg={}",
                    session.getConversationId(), e.getCode(), e.getMessage());
            sendError(ws, session.getConversationId(), e.getCode(), e.getMessage());
            return;
        } catch (Exception e) {
            log.warn("LLM call failed conversationId={}: {}", session.getConversationId(), e.getMessage());
            sendError(ws, session.getConversationId(), "LLM_TRANSIENT", e.getMessage());
            return;
        }

        String text = reply.getText() == null ? "" : reply.getText().trim();
        session.addUsage(reply.getUsage());

        if (SystemPromptBuilder.CALLBACK_NEEDED.equals(text)) {
            // Do not append the sentinel to history.
            send(ws, OutboundFrames.CallbackNeeded.builder()
                    .conversationId(session.getConversationId())
                    .replyToMessageId(replyToMessageId)
                    .build());
            log.info("Callback needed conversationId={}", session.getConversationId());
            return;
        }

        session.appendAssistant(text);
        send(ws, OutboundFrames.Response.builder()
                .conversationId(session.getConversationId())
                .replyToMessageId(replyToMessageId)
                .text(text)
                .usage(reply.getUsage())
                .build());
    }

    // ─── Lifecycle / utilities ───────────────────────────────────────────

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession ws, @NonNull CloseStatus status) {
        String conversationId = (String) ws.getAttributes().get(ATTR_CONVERSATION_ID);
        if (conversationId != null) {
            sessionRegistry.remove(conversationId);
            log.info("Session closed conversationId={} status={}", conversationId, status);
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