package com.aiassistant.callorchestration.telephony.twilio;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.voice.TextToSpeechProvider;
import com.aiassistant.callorchestration.voice.VoiceProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.twilio.security.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.Executor;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TwilioMediaStreamHandler implements TelephonyMediaStreamHandler {

    private static final Logger log = LoggerFactory.getLogger(TwilioMediaStreamHandler.class);
    private static final String SIGNATURE_HEADER = "X-Twilio-Signature";

    private final SecretsConfiguration secrets;
    private final com.aiassistant.callorchestration.configuration.ServiceConfiguration serviceConfiguration;
    private final SpeechToTextProvider speechToTextProvider;
    private final TextToSpeechProvider textToSpeechProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationCoordinator conversationCoordinator;
    @Qualifier("ttsExecutor")
    private final Executor ttsExecutor;
    /** Used to delay the botSpeaking=false flip until audio actually finishes
     *  playing on the caller's phone. Daemon so it doesn't block JVM exit. */
    private final java.util.concurrent.ScheduledExecutorService playbackEndScheduler =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "twilio-playback-end-scheduler");
                t.setDaemon(true);
                return t;
            });

    @Override
    public String providerId() {
        return "twilio";
    }

    @Override
    public boolean validateHandshake(ServerHttpRequest request) {
        String authToken = secrets.getTwilio() == null ? null : secrets.getTwilio().getAuthToken();
        if (authToken == null || authToken.isBlank()) {
            log.error("[twilio] auth token missing in secrets — rejecting handshake");
            return false;
        }

        HttpHeaders headers = request.getHeaders();
        String signature = headers.getFirst(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            log.warn("[twilio] missing {} header — rejecting handshake", SIGNATURE_HEADER);
            return false;
        }

        String fullUrl = reconstructPublicUrl(request);
        try {
            boolean ok = new RequestValidator(authToken).validate(fullUrl, Collections.emptyMap(), signature);
            if (!ok) {
                log.warn("[twilio] signature mismatch url={} sig={}", fullUrl, signature);
            }
            return ok;
        } catch (RuntimeException ex) {
            log.error("[twilio] signature validation threw exception", ex);
            return false;
        }
    }

    /**
     * Twilio Media Streams signs the URL EXACTLY as it appears in the TwiML
     * {@code <Stream url="wss://..."/>} — so we must reconstruct with the
     * {@code wss://} scheme, not {@code https://}. Behind a reverse proxy
     * (Cloudflare tunnel, ngrok, etc.), use X-Forwarded-Host to recover the
     * public hostname since the request arrives on localhost:8086 internally.
     */
    private String reconstructPublicUrl(ServerHttpRequest request) {
        HttpHeaders h = request.getHeaders();
        String host = h.getFirst("X-Forwarded-Host");
        if (host == null) host = h.getFirst("Host");
        if (host == null) {
            // Last-resort fallback — swap to wss:// since Twilio signs that scheme for Streams
            String uri = request.getURI().toString();
            if (uri.startsWith("https://")) return "wss://" + uri.substring("https://".length());
            if (uri.startsWith("http://"))  return "wss://" + uri.substring("http://".length());
            return uri;
        }
        String path = request.getURI().getRawPath();
        String query = request.getURI().getRawQuery();
        return "wss://" + host + path + (query == null ? "" : "?" + query);
    }

    @Override
    public void onConnect(CallSession session, Map<String, String> connectParams) {
        log.debug("[twilio] onConnect callId={} params={}", session.getCallId(), connectParams);
    }

    @Override
    public void onInboundFrame(CallSession session, String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("unknown");
            switch (event) {
                case "connected" -> log.debug("[twilio] <- CONNECTED callId={} protocol={} version={}",
                        session.getCallId(),
                        root.path("protocol").asText(),
                        root.path("version").asText());

                case "start" -> {
                    JsonNode start = root.path("start");
                    String streamSid = start.path("streamSid").asText();
                    String twilioCallSid = start.path("callSid").asText();
                    JsonNode fmt = start.path("mediaFormat");
                    Map<String, Object> info = new HashMap<>();
                    info.put("streamSid", streamSid);
                    info.put("twilioCallSid", twilioCallSid);
                    info.put("encoding", fmt.path("encoding").asText());
                    info.put("sampleRate", fmt.path("sampleRate").asInt());

                    // Stash streamSid + Twilio CallSid on the session — streamSid for
                    // outbound media frames, twilioCallSid for the REST hang-up call.
                    session.getProviderAttributes().put("streamSid", streamSid);
                    if (twilioCallSid != null && !twilioCallSid.isBlank()) {
                        session.getProviderAttributes().put("twilioCallSid", twilioCallSid);
                    }

                    Map<String, String> customParams = new HashMap<>();
                    JsonNode cp = start.path("customParameters");
                    Iterator<Map.Entry<String, JsonNode>> it = cp.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> e = it.next();
                        customParams.put(e.getKey(), e.getValue().asText());
                    }
                    log.info("[twilio] call started callId={} format={}@{}Hz",
                            session.getCallId(),
                            fmt.path("encoding").asText(), fmt.path("sampleRate").asInt());

                    // Hydrate session with tenant context from TwiML <Parameter> tags
                    if (customParams.containsKey("businessId")) {
                        session.setBusinessId(customParams.get("businessId"));
                    }
                    if (customParams.containsKey("businessName")) {
                        session.setBusinessName(customParams.get("businessName"));
                    }
                    if (customParams.containsKey("customerPhone")) {
                        session.setCustomerPhone(customParams.get("customerPhone"));
                    }


                    // Open AI conversation WS — sends INIT with business knowledge,
                    // streams MESSAGE/RESPONSE for this call.
                    try {
                        conversationCoordinator.onCallStart(
                                session.getCallId(),
                                new AiCallEventListener(session));
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open AI conversation WS callId={}",
                                session.getCallId(), ex);
                        // If AI WS fails to open, no point pumping audio into STT.
                        onProviderEvent(session, "start", info);
                        return;
                    }

                    // Open the STT streaming session — customer audio → text → coordinator.
                    try {
                        int sampleRate = fmt.path("sampleRate").asInt(8000);
                        SttSession stt = speechToTextProvider.openSession(
                                session.getCallId(),
                                AudioCodec.MULAW,
                                sampleRate,
                                event2 -> {
                                    // Bump activity timestamp on ANY non-empty transcript
                                    // (partial or final) so the silence detector doesn't
                                    // fire mid-utterance.
                                    String t = event2.text();
                                    var sttCfg2 = serviceConfiguration.getStt();
                                    Double bargeThreshold = sttCfg2 == null ? null : sttCfg2.getConfidenceThreshold();
                                    if (t != null && !t.isBlank()) {
                                        session.getProviderAttributes().put(
                                                "sttFinalAtMs", System.currentTimeMillis());
                                        // Barge-in gate. Three guards stack:
                                        //   (1) final-OR-length: avoid cutting the bot off on
                                        //       a stray short partial ("uh", a half-word).
                                        //   (2) confidence floor: when STT reports a confidence
                                        //       below the threshold, this looks like background
                                        //       noise / multi-speaker chatter, NOT a real
                                        //       caller utterance — skip the barge.
                                        //   (3) the bot must actually be speaking.
                                        int trimmedLen = t.trim().length();
                                        int minBargeLen = sttCfg2 == null ? 12 : sttCfg2.getBargeInMinLengthChars();
                                        boolean longEnough = event2.isFinal() || trimmedLen >= minBargeLen;
                                        boolean confidentEnough = event2.confidence() == null
                                                || bargeThreshold == null
                                                || event2.confidence() >= bargeThreshold;
                                        boolean enoughToBarge = longEnough && confidentEnough;
                                        if (enoughToBarge
                                                && Boolean.TRUE.equals(session.getProviderAttributes().get("botSpeaking"))) {
                                            java.util.concurrent.atomic.AtomicBoolean barged =
                                                    (java.util.concurrent.atomic.AtomicBoolean) session.getProviderAttributes()
                                                            .computeIfAbsent("barged", k -> new java.util.concurrent.atomic.AtomicBoolean());
                                            if (barged.compareAndSet(false, true)) {
                                                log.info("[barge-in] callId={} user spoke while bot speaking — clearing Twilio buffer",
                                                        session.getCallId());
                                                WebSocketSession bargeWs = (WebSocketSession) session.getProviderAttributes().get("ws");
                                                String bargeSid = (String) session.getProviderAttributes().get("streamSid");
                                                if (bargeWs != null && bargeWs.isOpen() && bargeSid != null) {
                                                    try {
                                                        ObjectNode clearFrame = objectMapper.createObjectNode();
                                                        clearFrame.put("event", "clear");
                                                        clearFrame.put("streamSid", bargeSid);
                                                        synchronized (bargeWs) {
                                                            bargeWs.sendMessage(new TextMessage(objectMapper.writeValueAsString(clearFrame)));
                                                        }
                                                    } catch (Exception ex) {
                                                        log.warn("[barge-in] send clear failed callId={}: {}",
                                                                session.getCallId(), ex.getMessage());
                                                    }
                                                }
                                                session.getProviderAttributes().put("botSpeaking", Boolean.FALSE);
                                            }
                                        }
                                    }
                                    if (event2.isFinal()) {
                                        // Confidence gate — sub-threshold transcripts get sent as
                                        // an UNCLEAR_MESSAGE frame; ai-conv short-circuits to a
                                        // canned "please repeat" reply (no LLM cost).
                                        var sttCfg = serviceConfiguration.getStt();
                                        Double threshold = sttCfg == null ? null : sttCfg.getConfidenceThreshold();
                                        boolean clear = !(threshold != null && event2.confidence() != null
                                                && event2.confidence() < threshold);
                                        if (!clear) {
                                            log.info("[stt] low-confidence callId={} conf={} text=\"{}\"",
                                                    session.getCallId(), event2.confidence(), event2.text());
                                        }
                                        long sttFinalAt = System.currentTimeMillis();
                                        session.getProviderAttributes().put("sttFinalAtMs", sttFinalAt);
                                        // Dedicated anchor for the user-to-speech metric, won't get
                                        // bumped by interim partials or by the bot-turn-end scheduler.
                                        session.getProviderAttributes().put("userStoppedAtMs", sttFinalAt);
                                        session.getProviderAttributes().remove("e2eLogged");
                                        session.getTranscript().add(CallSession.TranscriptEntry.builder()
                                                .speaker("CUSTOMER")
                                                .text(event2.text())
                                                .timestamp(java.time.Instant.now())
                                                .build());
                                        log.info("[latency] callId={} stage=stt-final",
                                                session.getCallId());
                                        conversationCoordinator.onCustomerUtterance(
                                                session.getCallId(),
                                                event2.text(),
                                                clear
                                        );
                                    }
                                }
                        );
                        session.getProviderAttributes().put("sttSession", stt);
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open STT session callId={}", session.getCallId(), ex);
                    }

                    onProviderEvent(session, "start", info);
                }

                case "media" -> {
                    JsonNode media = root.path("media");
                    String b64 = media.path("payload").asText();
                    byte[] audio = Base64.getDecoder().decode(b64);

                    Map<String, Object> attrs = session.getProviderAttributes();
                    if (!attrs.containsKey("firstFrameMs")) {
                        attrs.put("firstFrameMs", System.currentTimeMillis());
                    }
                    onAudioFrame(session, audio, AudioCodec.MULAW);
                }

                case "mark" -> {
                    String markName = root.path("mark").path("name").asText();
                    log.debug("[twilio] <- MARK callId={} name={}", session.getCallId(), markName);
                    // When Twilio echoes back the "ai-reply-end" mark we sent
                    // at the tail of the TTS stream, audio playback has actually
                    // finished on the caller's phone. Reset the silence clock
                    // NOW so we don't count playback time as user silence.
                    if ("ai-reply-end".equals(markName)) {
                        session.getProviderAttributes().put(
                                "sttFinalAtMs", System.currentTimeMillis());
                    }
                }

                case "stop" -> {
                    log.info("[twilio] call stopped callId={}", session.getCallId());
                    safeEndAiConversation(session.getCallId());
                    onProviderEvent(session, "stop", Map.of());
                }

                case "dtmf" -> log.info("[twilio] DTMF callId={} digit={}",
                        session.getCallId(), root.path("dtmf").path("digit").asText());

                default -> log.warn("[twilio] <- UNKNOWN event '{}' callId={} payload={}",
                        event, session.getCallId(), payload);
            }
        } catch (Exception ex) {
            log.error("[twilio] frame parse error callId={} payload={}",
                    session.getCallId(), payload, ex);
        }
    }

    @Override
    public void onAudioFrame(CallSession session, byte[] audioPayload, AudioCodec codec) {
        // Drop inbound audio while the bot is speaking. Without this the bot
        // hears its own TTS bleed back through the phone line and STT picks it
        // up as a "customer utterance" — derails the conversation. No barge-in
        // support today; this is the safe option.
        if (serviceConfiguration.getStt() != null
                && serviceConfiguration.getStt().isMuteWhileBotSpeaking()
                && Boolean.TRUE.equals(session.getProviderAttributes().get("botSpeaking"))) {
            return;
        }
        Object stt = session.getProviderAttributes().get("sttSession");
        if (stt instanceof SttSession sttSession) {
            sttSession.pushAudio(audioPayload);
        }
    }

    @Override
    public void onProviderEvent(CallSession session, String eventType, Map<String, Object> payload) {
        log.debug("[twilio] onProviderEvent callId={} event={} payload={}",
                session.getCallId(), eventType, payload);
    }

    @Override
    public void onDisconnect(CallSession session, String reason) {
        log.info("[twilio] onDisconnect callId={} reason={}", session.getCallId(), reason);
        safeEndAiConversation(session.getCallId());
    }

    @Override
    public byte[] encodeOutboundAudio(CallSession session, byte[] pcm16k) {
        return new byte[0];
    }

    private void safeEndAiConversation(String callId) {
        try {
            conversationCoordinator.onCallEnd(callId);
        } catch (Exception ex) {
            log.warn("[twilio] onCallEnd failed callId={}: {}", callId, ex.getMessage());
        }
    }

    /**
     * Listener handed to the coordinator for a single call. Holds the
     * {@link CallSession} so future TTS / outbound-media work can read
     * provider attributes (streamSid, codec) without a registry lookup.
     */
    @RequiredArgsConstructor
    private final class AiCallEventListener implements ConversationCoordinator.CallEventListener {

        private final CallSession session;
        /** Per-call buffer for streaming text — flushed to TTS at sentence boundaries. */
        private final StringBuilder ttsBuffer = new StringBuilder();

        @Override
        public void onAiReply(String callId, String text) {
            synthesize(callId, text, true);
        }

        @Override
        public void onAiReplyChunk(String callId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            synchronized (ttsBuffer) {
                ttsBuffer.append(deltaText);
                // Flush as soon as we have a sentence-boundary punctuation —
                // the earlier we hand text to TTS the lower the perceived
                // latency. Boundaries: . ! ? । (Devanagari danda) followed by
                // a space, or a newline.
                int flushAt = lastSentenceBoundary(ttsBuffer);
                if (flushAt > 0) {
                    String chunk = ttsBuffer.substring(0, flushAt).trim();
                    ttsBuffer.delete(0, flushAt);
                    if (!chunk.isEmpty()) {
                        synthesize(callId, chunk, false);
                    }
                }
            }
        }

        @Override
        public void onAiReplyDone(String callId) {
            String remaining;
            synchronized (ttsBuffer) {
                remaining = ttsBuffer.toString().trim();
                ttsBuffer.setLength(0);
            }
            if (!remaining.isEmpty()) {
                synthesize(callId, remaining, true);
            } else {
                String streamSid = (String) session.getProviderAttributes().get("streamSid");
                WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
                if (streamSid != null && ws != null && ws.isOpen()) {
                    ttsExecutor.execute(() -> {
                        try { sendMark(ws, streamSid, "ai-reply-end"); }
                        catch (Exception ex) {
                            log.warn("[twilio] mark send failed callId={}: {}", callId, ex.getMessage());
                        }
                    });
                }
            }
        }

        private void synthesize(String callId, String text, boolean isFinal) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) return;
            java.util.concurrent.atomic.AtomicInteger inflight =
                    (java.util.concurrent.atomic.AtomicInteger) session.getProviderAttributes()
                            .computeIfAbsent("ttsInflight", k -> new java.util.concurrent.atomic.AtomicInteger());
            // Fresh AI turn (no chunks in flight yet) → clear stale flags so this
            // reply can play out. Bargein and the audio-byte counter both reset.
            if (inflight.get() == 0) {
                java.util.concurrent.atomic.AtomicBoolean barged =
                        (java.util.concurrent.atomic.AtomicBoolean) session.getProviderAttributes()
                                .computeIfAbsent("barged", k -> new java.util.concurrent.atomic.AtomicBoolean());
                barged.set(false);
                java.util.concurrent.atomic.AtomicLong turnBytes =
                        (java.util.concurrent.atomic.AtomicLong) session.getProviderAttributes()
                                .computeIfAbsent("turnAudioBytes", k -> new java.util.concurrent.atomic.AtomicLong());
                turnBytes.set(0);
            }
            inflight.incrementAndGet();
            session.getProviderAttributes().put("botSpeaking", Boolean.TRUE);
            ttsExecutor.execute(() -> {
                java.util.concurrent.atomic.AtomicBoolean barged =
                        (java.util.concurrent.atomic.AtomicBoolean) session.getProviderAttributes()
                                .get("barged");
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    long ttsStart = System.currentTimeMillis();
                    long[] firstChunkAt = {-1};
                    textToSpeechProvider.synthesizeStream(text,
                            VoiceProfile.builder().language(session.getLanguage()).build(),
                            chunk -> {
                                // Drop further audio if user has barged in.
                                if (barged != null && barged.get()) return;
                                if (firstChunkAt[0] < 0) {
                                    firstChunkAt[0] = System.currentTimeMillis();
                                    log.info("[latency] callId={} stage=tts-first-byte ms={}",
                                            callId, firstChunkAt[0] - ttsStart);
                                    // user-to-speech: time from "user stopped speaking" (STT final)
                                    // to "bot's first audio chunk ready". Logged ONCE per customer
                                    // turn — on the first audio chunk of the first sentence after
                                    // STT final. Anchored on userStoppedAtMs which is NOT bumped
                                    // by interim STT partials, so this is a true round-trip metric.
                                    if (session.getProviderAttributes().putIfAbsent("e2eLogged", true) == null) {
                                        Object userStoppedAt = session.getProviderAttributes().get("userStoppedAtMs");
                                        if (userStoppedAt instanceof Long s && s > 0) {
                                            log.info("[latency] callId={} stage=user-to-speech ms={}",
                                                    callId, firstChunkAt[0] - s);
                                        }
                                    }
                                }
                                // Track audio bytes for THIS TTS turn so we can
                                // compute the exact playback duration on Twilio's
                                // side (mu-law @ 8000 samples/sec → 8 bytes/ms).
                                Object tb = session.getProviderAttributes().get("turnAudioBytes");
                                if (tb instanceof java.util.concurrent.atomic.AtomicLong c) {
                                    c.addAndGet(chunk.length);
                                }
                                sendMediaChunk(ws, streamSid, b64, chunk);
                            });
                    log.info("[latency] callId={} stage=tts-total ms={} chars={}",
                            callId, System.currentTimeMillis() - ttsStart, text.length());
                    if (isFinal && (barged == null || !barged.get())) {
                        sendMark(ws, streamSid, "ai-reply-end");
                    }
                } catch (Exception ex) {
                    log.warn("[twilio] TTS/send failed callId={}: {}", callId, ex.getMessage());
                } finally {
                    if (inflight.decrementAndGet() <= 0) {
                        // All TTS chunks for this turn have been sent to Twilio,
                        // but Twilio is still playing audio on the caller's phone.
                        // Compute the exact playback duration from total bytes sent
                        // (mu-law @ 8000 samples/sec = 8 bytes/ms). Then schedule
                        // the botSpeaking=false flip after that delay — the silence
                        // tick skips while botSpeaking=true, so the counter stays
                        // paused for the full bot turn (send + playback).
                        Object tb = session.getProviderAttributes().get("turnAudioBytes");
                        long bytes = (tb instanceof java.util.concurrent.atomic.AtomicLong c) ? c.get() : 0L;
                        long minPlayback = serviceConfiguration.getTelephony() == null
                                ? 200L : serviceConfiguration.getTelephony().getMinPlaybackMs();
                        long playbackMs = Math.max(minPlayback, bytes / AudioCodec.MULAW.bytesPerMs());
                        log.debug("[bot-turn-end] callId={} bytes={} playbackMs={} — pausing silence until playback completes",
                                callId, bytes, playbackMs);
                        playbackEndScheduler.schedule(() -> {
                            // Skip if another reply has already started.
                            if (inflight.get() != 0) return;
                            session.getProviderAttributes().put("botSpeaking", Boolean.FALSE);
                            long now = System.currentTimeMillis();
                            // MAX so a fresher user STT (e.g. mid-playback bargein)
                            // isn't clobbered.
                            Object cur = session.getProviderAttributes().get("sttFinalAtMs");
                            long prev = cur instanceof Long l ? l : 0L;
                            session.getProviderAttributes().put(
                                    "sttFinalAtMs", Math.max(prev, now));
                            // Full cycle metric: from "user stopped speaking" (STT final)
                            // to "bot finished speaking, mic is yours again". This is what
                            // a caller actually feels as the round-trip wait.
                            Object u = session.getProviderAttributes().get("userStoppedAtMs");
                            if (u instanceof Long uMs && uMs > 0) {
                                log.info("[latency] callId={} stage=full-turn ms={}", callId, now - uMs);
                            }
                            log.debug("[bot-turn-end] callId={} playback complete", callId);
                        }, playbackMs, java.util.concurrent.TimeUnit.MILLISECONDS);
                    }
                }
            });
        }

        /** Find the index just past the last clause/sentence boundary in the
         *  buffer. We flush at commas, semicolons, colons (in addition to
         *  terminal punctuation) AND at common conjunctions ("and", "but",
         *  "so", "or", "because") so each clause hits TTS as soon as it's
         *  ready — first audio fires within ~250 ms of stt-final instead of
         *  waiting for a full sentence. The conjunction itself stays with
         *  the next clause so the spoken cadence is natural. */
        private int lastSentenceBoundary(CharSequence s) {
            int n = s.length();
            int best = -1;
            // 1) Punctuation boundary — closest one to the end wins.
            for (int i = n - 1; i >= 0; i--) {
                char c = s.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == '।' || c == '\n'
                        || c == ',' || c == ';' || c == ':') {
                    // Require a following whitespace/newline (or end) to avoid splitting "Rs.500".
                    if (i == n - 1 || Character.isWhitespace(s.charAt(i + 1))) {
                        best = i + 1;
                        break;
                    }
                }
            }
            // 2) Conjunction boundary — flush BEFORE the conjunction so the
            //    " and ..." / " but ..." stays in the buffer for the next chunk.
            String[] conjunctions = { " and ", " but ", " so ", " or ", " because " };
            String str = s.toString();
            for (String w : conjunctions) {
                int idx = str.lastIndexOf(w);
                // Only honour conjunctions that come AFTER at least 6 chars of
                // text — avoids flushing a tiny "I or" type fragment.
                if (idx > 5 && idx > best) {
                    best = idx;
                }
            }
            return best;
        }

        private void sendMediaChunk(WebSocketSession ws, String streamSid, Base64.Encoder b64, byte[] chunk) {
            if (!ws.isOpen()) return;
            try {
                ObjectNode frame = objectMapper.createObjectNode();
                frame.put("event", "media");
                frame.put("streamSid", streamSid);
                frame.putObject("media").put("payload", b64.encodeToString(chunk));
                synchronized (ws) {
                    ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private void sendMark(WebSocketSession ws, String streamSid, String name) throws Exception {
            if (!ws.isOpen()) return;
            ObjectNode mark = objectMapper.createObjectNode();
            mark.put("event", "mark");
            mark.put("streamSid", streamSid);
            mark.putObject("mark").put("name", name);
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(mark)));
            }
        }

        @Override
        public void onCallbackNeeded(String callId) {
            log.info("[ai] callback needed callId={}", callId);
            // TODO: announce callback via TTS, then hangup the Twilio stream
        }

        @Override
        public void onHangup(String callId, String spokenText, String reason) {
            String twilioCallSid = (String) session.getProviderAttributes().get("twilioCallSid");
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            log.info("[ai] hangup callId={} reason={} sid={}", callId, reason, twilioCallSid);

            // Run TTS + Twilio terminate inline on the same executor thread so
            // the farewell finishes playing before the call drops.
            ttsExecutor.execute(() -> {
                long bytesSent = 0L;
                try {
                    if (spokenText != null && !spokenText.isBlank()
                            && streamSid != null && ws != null && ws.isOpen()) {
                        Base64.Encoder b64 = Base64.getEncoder();
                        long[] bytes = {0L};
                        textToSpeechProvider.synthesizeStream(spokenText,
                                VoiceProfile.builder().language(session.getLanguage()).build(),
                                chunk -> {
                                    bytes[0] += chunk.length;
                                    sendMediaChunk(ws, streamSid, b64, chunk);
                                });
                        bytesSent = bytes[0];
                        sendMark(ws, streamSid, "ai-hangup-end");
                        // Sleep for the full playback duration + configurable
                        // tail so the entire farewell sentence finishes in the
                        // caller's ear before the call drops. Tail covers Twilio
                        // buffering and carrier delivery delay.
                        long hangupTail = serviceConfiguration.getTelephony() == null
                                ? 500L : serviceConfiguration.getTelephony().getHangupTailMs();
                        long playbackMs = bytesSent / AudioCodec.MULAW.bytesPerMs() + hangupTail;
                        log.info("[twilio] hangup TTS bytes={} playbackMs={} callId={}",
                                bytesSent, playbackMs, callId);
                        Thread.sleep(playbackMs);
                    }
                } catch (Exception ex) {
                    log.warn("[twilio] hangup TTS failed callId={}: {}", callId, ex.getMessage());
                }
                hangupTwilioCall(callId, twilioCallSid);
            });
        }

        private void hangupTwilioCall(String callId, String twilioCallSid) {
            if (twilioCallSid == null || twilioCallSid.isBlank()) {
                log.warn("[twilio] cannot hangup — no twilioCallSid callId={}", callId);
                return;
            }
            SecretsConfiguration.Twilio tw = secrets.getTwilio();
            if (tw == null || tw.getAccountSid() == null || tw.getAuthToken() == null) {
                log.warn("[twilio] cannot hangup — twilio credentials missing");
                return;
            }
            ensureTwilioInited(tw.getAccountSid(), tw.getAuthToken());
            try {
                com.twilio.rest.api.v2010.account.Call.updater(twilioCallSid)
                        .setStatus(com.twilio.rest.api.v2010.account.Call.UpdateStatus.COMPLETED)
                        .update();
                log.info("[twilio] call completed via REST callId={} sid={}", callId, twilioCallSid);
            } catch (Exception ex) {
                log.warn("[twilio] hangup REST failed callId={}: {}", callId, ex.getMessage());
            }
        }
    }

    private static volatile boolean twilioInited = false;
    private static void ensureTwilioInited(String accountSid, String authToken) {
        if (twilioInited) return;
        synchronized (TwilioMediaStreamHandler.class) {
            if (!twilioInited) {
                com.twilio.Twilio.init(accountSid, authToken);
                twilioInited = true;
            }
        }
    }
}