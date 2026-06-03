package com.aiassistant.callorchestration.telephony.exotel;

import com.aiassistant.callorchestration.telephony.BaseBargeInHandler;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class ExotelBargeInHandler extends BaseBargeInHandler {

    private static final Logger log = LoggerFactory.getLogger(ExotelBargeInHandler.class);

    private final ObjectMapper objectMapper;

    public ExotelBargeInHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void clearCarrierBuffer(CallSession session) {
        WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
        String streamSid = (String) session.getProviderAttributes().get("streamSid");

        if (ws == null || !ws.isOpen() || streamSid == null) {
            log.warn("[barge-in] cannot clear — missing ws/streamSid callId={}",
                    session.getCallId());
            return;
        }

        try {
            ObjectNode frame = objectMapper.createObjectNode();
            frame.put("event", "clear");
            frame.put("stream_sid", streamSid);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
            }
            log.info("[barge-in] clear sent callId={} streamSid={}",
                    session.getCallId(), streamSid);
        } catch (Exception ex) {
            log.warn("[barge-in] clear failed callId={}: {}",
                    session.getCallId(), ex.getMessage());
        }
    }
}
