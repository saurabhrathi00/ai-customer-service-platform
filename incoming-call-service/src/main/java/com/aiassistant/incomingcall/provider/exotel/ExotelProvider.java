package com.aiassistant.incomingcall.provider.exotel;

import com.aiassistant.incomingcall.configuration.SecretsConfiguration;
import com.aiassistant.incomingcall.provider.IncomingCallRequest;
import com.aiassistant.incomingcall.provider.StreamHandoff;
import com.aiassistant.incomingcall.provider.TelephonyProvider;
import com.aiassistant.incomingcall.provider.TelephonyResponse;
import com.aiassistant.incomingcall.provider.TelephonySignatureInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Exotel Voicebot Applet provider. Exotel POSTs a JSON webhook on inbound
 * call; this service returns {@code {"url": "wss://..."}} pointing to
 * call-orchestration-service's Exotel media-stream endpoint. Exotel then
 * opens a bidirectional WebSocket on that URL for PCM16/mulaw audio streaming.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "secrets.exotel", name = "apiKey")
public class ExotelProvider implements TelephonyProvider {

    private static final Logger log = LoggerFactory.getLogger(ExotelProvider.class);
    private static final String PARSED_BODY_ATTR = "exotel.parsedBody";

    private final SecretsConfiguration secretsConfiguration;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "exotel";
    }

    @Override
    public void verifySignature(HttpServletRequest request) {
        // Log all headers and body for debugging Exotel's webhook format
        StringBuilder hdrs = new StringBuilder();
        java.util.Collections.list(request.getHeaderNames())
                .forEach(h -> hdrs.append(h).append("=").append(request.getHeader(h)).append("; "));
        log.info("[exotel] incoming webhook headers: {}", hdrs);

        // Parse and stash the JSON body for parseRequest()
        try {
            byte[] body = request.getInputStream().readAllBytes();
            log.info("[exotel] incoming webhook body ({} bytes): {}", body.length,
                    new String(body, java.nio.charset.StandardCharsets.UTF_8));
            if (body.length > 0) {
                JsonNode parsed = objectMapper.readTree(body);
                request.setAttribute(PARSED_BODY_ATTR, parsed);
            }
        } catch (IOException ex) {
            throw new TelephonySignatureInvalidException("Failed to read Exotel request body: " + ex.getMessage());
        }
    }

    @Override
    public IncomingCallRequest parseRequest(HttpServletRequest request) {
        JsonNode body = (JsonNode) request.getAttribute(PARSED_BODY_ATTR);

        // Exotel webhook payload fields
        String callSid = extractField(body, request, "CallSid", "call_sid", "callSid");
        String from = extractField(body, request, "From", "from", "CallFrom");
        String to = extractField(body, request, "To", "to", "CallTo", "DialWhomNumber");
        String status = extractField(body, request, "Status", "status", "CallStatus");
        log.info("[exotel] raw parsed fields — From={} To={} CallSid={} Status={}", from, to, callSid, status);

        return IncomingCallRequest.builder()
                .callId(callSid)
                .fromNumber(from)
                .toNumber(to)
                .callStatus(status)
                .build();
    }

    @Override
    public TelephonyResponse buildStreamHandoff(StreamHandoff handoff) {
        // Exotel Voicebot Applet expects: {"url": "wss://..."}
        // Exotel supports max 3 query params, total length ≤256 chars.
        // Don't URL-encode values — Exotel's parser may strip encoded params.
        // customerPhone uses raw digits (no '+' prefix) to avoid encoding issues.
        String phone = handoff.getCustomerPhone();
        if (phone != null && phone.startsWith("+")) phone = phone.substring(1);
        String bName = handoff.getBusinessName();
        if (bName != null) bName = bName.replaceAll("[^a-zA-Z0-9 ]", "").trim();
        String wsUrl = handoff.getWsUrl()
                + "?businessId=" + handoff.getBusinessId()
                + "&customerPhone=" + phone
                + (bName != null && !bName.isEmpty() ? "&businessName=" + bName : "");

        String json = "{\"url\":\"" + escapeJson(wsUrl) + "\"}";
        log.info("[exotel] handoff callId={} wsUrl={}", handoff.getCallId(), wsUrl);
        return new TelephonyResponse(json, MediaType.APPLICATION_JSON);
    }

    @Override
    public TelephonyResponse buildUnknownNumberResponse() {
        // Exotel expects a JSON response; empty url or an error signals no stream
        return new TelephonyResponse("{\"error\":\"Number not in service\"}", MediaType.APPLICATION_JSON);
    }

    private String extractField(JsonNode body, HttpServletRequest request, String... names) {
        // Try JSON body first, then request parameters
        if (body != null) {
            for (String name : names) {
                JsonNode node = body.path(name);
                if (!node.isMissingNode() && !node.isNull()) {
                    return node.asText();
                }
            }
        }
        for (String name : names) {
            String val = request.getParameter(name);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }

    private static String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
