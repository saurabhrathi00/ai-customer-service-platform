package com.aiassistant.callorchestration.telephony.enablex;

import com.aiassistant.callorchestration.telephony.BaseBargeInHandler;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class EnableXBargeInHandler extends BaseBargeInHandler {

    private static final Logger log = LoggerFactory.getLogger(EnableXBargeInHandler.class);

    private final ObjectMapper objectMapper;

    public EnableXBargeInHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void clearCarrierBuffer(CallSession session) {
        WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
        String streamId = (String) session.getProviderAttributes().get("streamSid");
        String voiceId = (String) session.getProviderAttributes().get("enablexCallSid");

        if (ws == null || !ws.isOpen() || streamId == null || voiceId == null) {
            log.warn("[barge-in] cannot clear_media — missing ws/streamId/voiceId callId={}",
                    session.getCallId());
            return;
        }

        try {
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("event", "clear_media");
            frame.put("stream_id", streamId);
            frame.put("voice_id", voiceId);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
            log.info("[barge-in] clear_media sent callId={} streamId={} voiceId={}",
                    session.getCallId(), streamId, voiceId);
        } catch (Exception ex) {
            log.warn("[barge-in] clear_media failed callId={}: {}",
                    session.getCallId(), ex.getMessage());
        }
    }
}
