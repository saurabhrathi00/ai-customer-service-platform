package com.aiassistant.callorchestration.telephony.twilio;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.configuration.SecretsConfiguration;
import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.telephony.BargeInHandler;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.TelephonyMediaStreamHandler;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.voice.FillerAudioCache;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import java.util.Base64;
import java.util.Collections;
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
    private final AiConversationWsClient aiConversationWsClient;
    private final FillerAudioCache fillerAudioCache;
    @Qualifier("ttsExecutor")
    private final Executor ttsExecutor;
    @Qualifier("silenceWatchdogScheduler")
    private final ScheduledExecutorService silenceWatchdogScheduler;

    /** Barge-in detection knobs. Conservative defaults — tune in
     *  configs.barge.* if false positives or missed barges appear.
     *  Interim partials need {@code BARGE_MIN_CHARS} to fire so quick
     *  "yes" / "haan" / "hmm" acknowledgements don't cut the bot mid
     *  sentence. Finals always pass length. */
    private static final int    BARGE_MIN_CHARS = 14;
    private static final double BARGE_CONF_MIN  = 0.5;
    /** Bot self-barge protection. The first few hundred ms of any bot
     *  utterance is immune to barge-in so an echo of the bot's own opening
     *  syllable can't self-cut the reply. Kept short (400ms) so real
     *  callers who start talking immediately don't get suppressed —
     *  Twilio's {@code track=inbound_track} already filters most of the
     *  outbound audio at the carrier. */
    private static final long   BARGE_MIN_BOT_SPEAKING_MS = 400L;
    /** Inter-sentence silence beyond which we treat the next chunk as a NEW
     *  bot-speaking burst (and reset the grace clock). Below this, back-to-back
     *  sentences share the original burst's start time so the caller can still
     *  interrupt sentence #2 of a multi-sentence reply. */
    private static final long   BURST_GAP_MS = 600L;

    /** Drop STT finals shorter than this. Threshold is intentionally tiny
     *  (2 chars) — anything 1 char is almost always noise / mis-recognition,
     *  but "Ok", "no", "हाँ" etc. are legitimate short replies that must
     *  pass through. */
    private static final int    MIN_FORWARD_CHARS = 2;

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

                    // Stash streamSid + Twilio CallSid on the session — streamSid for
                    // outbound media frames, twilioCallSid for the REST hang-up call.
                    session.getProviderAttributes().put("streamSid", streamSid);
                    if (twilioCallSid != null && !twilioCallSid.isBlank()) {
                        session.getProviderAttributes().put("twilioCallSid", twilioCallSid);
                    }

                    log.info("[twilio] call started callId={} format={}@{}Hz",
                            session.getCallId(),
                            fmt.path("encoding").asText(), fmt.path("sampleRate").asInt());

                    // Hydrate session with tenant context from TwiML <Parameter> tags
                    JsonNode cp = start.path("customParameters");
                    if (cp.hasNonNull("businessId"))    session.setBusinessId(cp.path("businessId").asText());
                    if (cp.hasNonNull("businessName"))  session.setBusinessName(cp.path("businessName").asText());
                    if (cp.hasNonNull("customerPhone")) session.setCustomerPhone(cp.path("customerPhone").asText());


                    // Per-call barge-in handler — owns the per-utterance
                    // idempotency flag and fans cancellation out to Twilio,
                    // ai-conv and local TTS in one place.
                    BargeInHandler bargeHandler = new BargeInHandler(
                            BARGE_MIN_CHARS, BARGE_CONF_MIN, BARGE_MIN_BOT_SPEAKING_MS,
                            objectMapper, aiConversationWsClient);
                    session.getProviderAttributes().put("bargeInHandler", bargeHandler);

                    // Open AI conversation WS — sends INIT with business knowledge,
                    // streams MESSAGE/RESPONSE for this call.
                    AiCallEventListener listener = new AiCallEventListener(session);
                    session.getProviderAttributes().put("aiCallListener", listener);
                    // Anchor the silence watchdog at call start — give the
                    // caller ~nudgeAfterMs to speak from now (or from greeting end).
                    session.setLastCallerActivityMs(System.currentTimeMillis());
                    startSilenceWatchdog(session, listener);
                    try {
                        conversationCoordinator.onCallStart(session.getCallId(), listener);
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open AI conversation WS callId={}",
                                session.getCallId(), ex);
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
                                    String text = event2.text();
                                    if (text == null || text.isBlank()) return;

                                    // Drop every STT event until the greeting
                                    // has finished playing. The caller hasn't
                                    // even heard the prompt yet, so anything
                                    // STT picks up here is either noise or the
                                    // start of speech that will be re-spoken
                                    // once the bot stops — better to ignore.
                                    if (!session.getGreetingDone().get()) {
                                        log.debug("[stt] dropped during greeting callId={} text=\"{}\"",
                                                session.getCallId(), text);
                                        return;
                                    }

                                    // Any transcript activity (partial or final)
                                    // resets the silence watchdog — caller is alive.
                                    session.setLastCallerActivityMs(System.currentTimeMillis());
                                    session.setSilenceNudgedAtMs(0L);

                                    // Barge-in: BargeInHandler runs three gates
                                    // (bot speaking, length, confidence) on every
                                    // STT event — interim or final — and on the
                                    // first hit per utterance fires three
                                    // cancellations (Twilio clear, ai-conv
                                    // BARGE_IN, local TTS epoch bump).
                                    bargeHandler.checkAndBarge(session, event2);

                                    if (!event2.isFinal()) return;

                                    // Noise filter — only drop transcripts that
                                    // are clearly garbage. We previously had a
                                    // confidence floor too, but it filtered
                                    // out legitimate short replies ("Ok",
                                    // "Hello") that Deepgram tagged at 0.5–0.65
                                    // confidence — leaving the caller wondering
                                    // why the bot ignored them. Trust Deepgram
                                    // on confidence; only veto 1-char snippets.
                                    String trimmed = text.trim();
                                    if (trimmed.length() < MIN_FORWARD_CHARS) {
                                        log.info("[stt] DROP-NOISE callId={} reason=too-short len={} text=\"{}\"",
                                                session.getCallId(), trimmed.length(), text);
                                        bargeHandler.reset();
                                        return;
                                    }

                                    // Play a "thinking" filler immediately so
                                    // the caller hears something during the
                                    // LLM+TTS round-trip. Queued on the same
                                    // ttsTail chain — the real reply lands
                                    // right after the filler finishes.
                                    if (fillerAudioCache.isEnabled()) {
                                        AiCallEventListener l = (AiCallEventListener)
                                                session.getProviderAttributes().get("aiCallListener");
                                        if (l != null) l.maybePlayFiller(text);
                                    }
                                    conversationCoordinator.onCustomerUtterance(
                                            session.getCallId(), text, /*clear=*/ true);
                                    // The final has been forwarded — re-arm the
                                    // barge-in handler so the next utterance is
                                    // eligible to interrupt the upcoming reply.
                                    bargeHandler.reset();
                                }
                        );
                        session.getProviderAttributes().put("sttSession", stt);
                    } catch (Exception ex) {
                        log.error("[twilio] failed to open STT session callId={}", session.getCallId(), ex);
                    }
                }

                case "media" -> {
                    byte[] audio = Base64.getDecoder().decode(root.path("media").path("payload").asText());
                    onAudioFrame(session, audio);
                }

                case "mark" -> log.debug("[twilio] <- MARK callId={} name={}",
                        session.getCallId(), root.path("mark").path("name").asText());

                case "stop" -> {
                    log.info("[twilio] call stopped callId={}", session.getCallId());
                    cancelSilenceWatchdog(session);
                    safeEndAiConversation(session.getCallId());
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

    /** Interrogative tokens — presence of any one of these promotes the
     *  utterance to "needs a filler" regardless of length. Lowercased; we
     *  match on word boundaries via {@code split("[^a-zA-Z]+")}. */
    private static final java.util.Set<String> QUESTION_WORDS = java.util.Set.of(
            // English
            "what", "where", "when", "why", "who", "how", "which", "whose",
            "can", "could", "do", "does", "did", "will", "would", "should",
            "is", "are", "am", "was", "were", "may", "might", "tell", "explain",
            // Hindi / Hinglish (Latin transliteration)
            "kya", "kaise", "kahan", "kab", "kyun", "kyon", "kaun", "kitna",
            "kitne", "kitni", "batao", "bataiye", "samjhao", "dikhao",
            "milega", "milegi", "hoga", "hogi");

    /** Minimum trimmed length below which we treat the utterance as a
     *  short ack ("yes", "haan", "ok thanks") even when it contains a
     *  question word. Prevents fillers on tiny one-word turns. */
    private static final int FILLER_MIN_CHARS = 14;

    /** Filler fires ONLY on actual questions, never on statements. Filler
     *  text is shaped like "okay let me see" / "haan ji ek minute" — that
     *  only makes sense as a response to a question. On a statement like
     *  "I want to know about your products" the filler reads as a
     *  non-sequitur. Two signals make us treat the utterance as a
     *  question: a trailing '?' (Deepgram smart_format reliably adds it),
     *  or an interrogative word as the FIRST content token. Question
     *  words buried mid-sentence ("I want to know how to call you") used
     *  to false-positive when we scanned all tokens. */
    private static boolean looksLikeFillerWorthy(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        if (trimmed.length() < FILLER_MIN_CHARS) return false;
        if (trimmed.endsWith("?")) return true;
        String first = firstAlphaToken(trimmed);
        return first != null && QUESTION_WORDS.contains(first);
    }

    /** First contiguous run of ASCII letters in the text, lowercased.
     *  Returns null if the text has no leading letters. */
    private static String firstAlphaToken(String text) {
        int n = text.length();
        int i = 0;
        while (i < n && !Character.isLetter(text.charAt(i))) i++;
        int start = i;
        while (i < n && (text.charAt(i) >= 'a' && text.charAt(i) <= 'z'
                       || text.charAt(i) >= 'A' && text.charAt(i) <= 'Z')) i++;
        return i == start ? null : text.substring(start, i).toLowerCase(java.util.Locale.ROOT);
    }

    private void onAudioFrame(CallSession session, byte[] audioPayload) {
        Object stt = session.getProviderAttributes().get("sttSession");
        if (stt instanceof SttSession sttSession) {
            sttSession.pushAudio(audioPayload);
        }
        // VAD-lite: bump caller-activity timestamp when the frame carries
        // real voice. Twilio sends ~20ms mu-law frames continuously even
        // during silence — those decode to the mu-law silence byte (0xFF).
        // Counting non-silence samples gives us a free 20ms-granularity
        // signal that's far more responsive than STT partials, so the
        // silence watchdog won't nudge a caller who's in the middle of
        // a long sentence. Skip until the greeting has finished — bot's
        // own audio routed by the carrier shouldn't count as caller voice.
        if (session.getGreetingDone().get() && hasVoice(audioPayload)) {
            session.setLastCallerActivityMs(System.currentTimeMillis());
            session.setSilenceNudgedAtMs(0L);
        }
    }

    /** Mu-law silence is 0xFF; voice samples vary across the full byte range.
     *  We declare "voice present" when more than {@code VAD_MIN_VOICE_BYTES}
     *  bytes in the frame differ from the silence value. Tuned to be
     *  insensitive to faint line noise but trigger on real speech. */
    private static final int VAD_MIN_VOICE_BYTES = 30;
    private static boolean hasVoice(byte[] mulawFrame) {
        if (mulawFrame == null || mulawFrame.length == 0) return false;
        int nonSilence = 0;
        for (byte b : mulawFrame) {
            if (b != (byte) 0xFF && b != (byte) 0x7F) {
                nonSilence++;
                if (nonSilence >= VAD_MIN_VOICE_BYTES) return true;
            }
        }
        return false;
    }

    @Override
    public void onDisconnect(CallSession session, String reason) {
        log.info("[twilio] onDisconnect callId={} reason={}", session.getCallId(), reason);
        cancelSilenceWatchdog(session);
        safeEndAiConversation(session.getCallId());
    }

    // ─── Silence watchdog ─────────────────────────────────────────────────

    /**
     * Periodic check: if the caller has been silent for nudgeAfterMs, play a
     * "are you still there?" prompt; if they stay silent for another
     * hangupAfterNudgeMs, end the call politely. Skips entirely when
     * configs.silence.enabled=false.
     */
    private void startSilenceWatchdog(CallSession session, AiCallEventListener listener) {
        ServiceConfiguration.Silence cfg = serviceConfiguration.getSilence();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("[silence] watchdog disabled callId={}", session.getCallId());
            return;
        }
        long intervalMs = cfg.getCheckIntervalMs();
        ScheduledFuture<?> task = silenceWatchdogScheduler.scheduleWithFixedDelay(
                () -> tickSilenceWatchdog(session, listener, cfg),
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        session.getProviderAttributes().put("silenceWatchdog", task);
        log.info("[silence] watchdog started callId={} nudgeAfterMs={} hangupAfterNudgeMs={}",
                session.getCallId(), cfg.getNudgeAfterMs(), cfg.getHangupAfterNudgeMs());
    }

    private void cancelSilenceWatchdog(CallSession session) {
        Object t = session.getProviderAttributes().remove("silenceWatchdog");
        if (t instanceof ScheduledFuture<?> f) {
            f.cancel(false);
            log.info("[silence] watchdog cancelled callId={}", session.getCallId());
        }
    }

    private void tickSilenceWatchdog(CallSession session, AiCallEventListener listener,
                                     ServiceConfiguration.Silence cfg) {
        try {
            // Don't count time while the bot is mid-reply — only the caller's
            // silence matters. Each TTS chunk bumps lastTtsActivityMs; when
            // bot stops, we treat the silence clock as restarting from there.
            long now = System.currentTimeMillis();
            long lastBot = session.getLastTtsActivityMs();
            long lastCaller = session.getLastCallerActivityMs();
            long anchor = Math.max(lastBot, lastCaller);
            long silenceMs = now - anchor;

            // If bot is currently speaking or within carrier tail, no nudge.
            if (BargeInHandler.isBotSpeaking(session)) return;

            long nudgedAt = session.getSilenceNudgedAtMs();
            if (nudgedAt > 0) {
                // We already nudged. If still silent past hangup window, end call.
                long sinceNudge = now - nudgedAt;
                if (sinceNudge >= cfg.getHangupAfterNudgeMs()) {
                    log.info("[silence] HANGUP callId={} sinceNudgeMs={}",
                            session.getCallId(), sinceNudge);
                    cancelSilenceWatchdog(session);
                    String farewell = pickByLang(session,
                            cfg.getFarewellTextHi(), cfg.getFarewellTextEn());
                    listener.onHangup(session.getCallId(), farewell, "SILENCE");
                }
                // else: still waiting for caller post-nudge
                return;
            }

            if (silenceMs >= cfg.getNudgeAfterMs()) {
                String nudge = pickByLang(session,
                        cfg.getNudgeTextHi(), cfg.getNudgeTextEn());
                log.info("[silence] NUDGE callId={} silenceMs={} text=\"{}\"",
                        session.getCallId(), silenceMs, nudge);
                session.setSilenceNudgedAtMs(now);
                listener.onAiReply(session.getCallId(), nudge);
            }
        } catch (Exception ex) {
            log.warn("[silence] watchdog tick failed callId={}: {}",
                    session.getCallId(), ex.getMessage());
        }
    }

    /** Pick the Hindi or English variant based on session.getLanguage().
     *  Defaults to English when no language hint is available. */
    private static String pickByLang(CallSession session, String hi, String en) {
        String lang = session.getLanguage();
        if (lang != null && lang.toLowerCase().startsWith("hi")) return hi;
        return en;
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
        /** Per-call serial queue. Each TTS task chains off the previous one
         *  so audio frames reach Twilio strictly in the order ai-conv
         *  emitted them — parallel TTS would interleave frames and reset
         *  prosody mid-sentence, which sounded broken. */
        private final java.util.concurrent.atomic.AtomicReference<java.util.concurrent.CompletableFuture<Void>> ttsTail
                = new java.util.concurrent.atomic.AtomicReference<>(java.util.concurrent.CompletableFuture.completedFuture(null));

        /** Wall-clock of the last filler we played. Used to enforce a
         *  minimum gap so quick back-to-back turns don't stutter
         *  "ek sec... ek sec...". */
        private volatile long lastFillerAtMs = 0L;

        /** Max fillers we'll chain per single user turn before giving up on
         *  the LLM and falling back to the canned trouble message. ~3 keeps
         *  the caller engaged for roughly the longest reasonable wait
         *  (~6–8 s of "thinking" before we admit defeat). */
        private static final int FILLER_CHAIN_MAX = 3;
        /** Quiet pause inserted between chained fillers so they don't
         *  sound like one giant run-on phrase. */
        private static final long FILLER_CHAIN_GAP_MS = 600L;
        /** Set true the moment a real reply lands (DELTA or full RESPONSE)
         *  for the in-flight turn — stops the filler chain. Reset by
         *  {@link #maybePlayFiller} when a new user turn begins. */
        private final java.util.concurrent.atomic.AtomicBoolean replyStartedForTurn =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        /** Number of fillers queued so far for the in-flight user turn. */
        private final java.util.concurrent.atomic.AtomicInteger fillerChainCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        /** Epoch snapshot when the current filler chain started. If barge-in
         *  bumps the epoch, chainTick sees the mismatch and stops chaining. */
        private volatile long fillerChainEpoch = 0L;

        /**
         * Play a cached "thinking" clip in the caller's language IF:
         *  - the cache is enabled and has a clip for that language, AND
         *  - we haven't played one within the configured min-gap.
         * Queues onto the same ttsTail so the real reply plays right after
         * the filler finishes — no overlap, no reordering.
         */
        public void maybePlayFiller(String sttText) {
            if (!fillerAudioCache.isEnabled()) {
                log.info("[filler] skip callId={} reason=disabled", session.getCallId());
                return;
            }
            // Substance gate — skip filler ONLY for short pure-ack utterances
            // ("yes", "ok thanks", "haan bhai"). Anything longer OR anything
            // containing a question word fires a filler. We don't rely on
            // STT-added '?' alone because Deepgram smart_format often misses
            // question intonation in natural speech.
            String trimmed = sttText == null ? "" : sttText.trim();
            long now = System.currentTimeMillis();
            long sinceLast = now - lastFillerAtMs;
            if (sinceLast < fillerAudioCache.getMinGapMs()) {
                log.info("[filler] skip callId={} reason=minGap sinceLast={}ms", session.getCallId(), sinceLast);
                return;
            }
            boolean isQuestion = looksLikeFillerWorthy(trimmed);
            byte[] clip = isQuestion
                    ? fillerAudioCache.pickForText(sttText)
                    : fillerAudioCache.pickAckForText(sttText);
            if (clip == null || clip.length == 0) {
                log.info("[filler] skip callId={} reason=no-clip-for-language kind={} text=\"{}\"",
                        session.getCallId(), isQuestion ? "question" : "ack", sttText);
                return;
            }
            lastFillerAtMs = now;
            // Reset chain state every turn so the next reply can stop future
            // chained fillers. Statements get exactly ONE short ack clip —
            // chaining "Got it... Noted... Understood..." would feel awful.
            replyStartedForTurn.set(false);
            fillerChainCount.set(0);
            fillerChainEpoch = session.getTtsEpoch().get();
            if (isQuestion) {
                queueChainedFiller(sttText, clip);
            } else {
                long delay = fillerAudioCache.getStartDelayMs();
                log.debug("[filler] ack queueing callId={} bytes={} epoch={} startDelayMs={} lang={}",
                        session.getCallId(), clip.length, session.getTtsEpoch().get(), delay,
                        FillerAudioCache.looksHindi(sttText) ? "hi" : "en");
                queueRawAudio(clip, "ack", delay, /*countAsBotSpeaking=*/true);
            }
        }

        /** Queue one filler and schedule a follow-up check. If by the time
         *  the clip finishes the real reply still hasn't arrived AND we're
         *  under {@link #FILLER_CHAIN_MAX}, the check fires another filler.
         *  When the max is reached without a reply, we trigger the error
         *  fallback so the caller isn't left in silence. */
        private void queueChainedFiller(String sttText, byte[] clip) {
            int idx = fillerChainCount.incrementAndGet();
            long delay = (idx == 1) ? fillerAudioCache.getStartDelayMs() : FILLER_CHAIN_GAP_MS;
            log.debug("[filler] chain={} queueing callId={} bytes={} epoch={} startDelayMs={} lang={}",
                    idx, session.getCallId(), clip.length, session.getTtsEpoch().get(), delay,
                    FillerAudioCache.looksHindi(sttText) ? "hi" : "en");
            queueRawAudio(clip, "filler-" + idx, delay, /*countAsBotSpeaking=*/true);

            // mu-law @ 8kHz = 8 bytes per ms. Schedule the next-check a touch
            // after this clip finishes so the chain doesn't overlap itself.
            long approxClipMs = clip.length / 8L;
            long checkAfterMs = delay + approxClipMs + 200L;
            silenceWatchdogScheduler.schedule(
                    () -> chainTick(sttText), checkAfterMs, TimeUnit.MILLISECONDS);
        }

        /** Decide what to do after one chained filler finished playing. */
        private void chainTick(String sttText) {
            if (replyStartedForTurn.get()) return;          // reply arrived — chain done
            if (session.getEndingCall().get()) return;      // call winding down — drop
            if (session.getTtsEpoch().get() != fillerChainEpoch) return; // barged — stop chain
            if (fillerChainCount.get() >= FILLER_CHAIN_MAX) {
                log.warn("[filler-chain] exhausted callId={} after {} fillers — firing fallback",
                        session.getCallId(), FILLER_CHAIN_MAX);
                playTroubleFallback();
                return;
            }
            byte[] next = fillerAudioCache.pickForText(sttText);
            if (next == null || next.length == 0) {
                playTroubleFallback();
                return;
            }
            queueChainedFiller(sttText, next);
        }

        /** Speak a canned "I'm having trouble" message. Used when the LLM
         *  errors out OR when the filler chain exhausts with no reply. Goes
         *  through {@link #synthesize} so it rides the normal TTS chain (any
         *  in-flight filler completes first — we never cut a filler off). */
        private void playTroubleFallback() {
            // Latch the chain off so a late reply or a second error doesn't
            // pile a second fallback on top.
            if (!replyStartedForTurn.compareAndSet(false, true)) return;
            String msg = "Sorry, I'm having a little trouble right now. Could you please ask me again?";
            log.warn("[fallback] callId={} text=\"{}\"", session.getCallId(), msg);
            session.getTranscript().add(CallSession.TranscriptEntry.builder()
                    .speaker("assistant")
                    .text(msg)
                    .timestamp(java.time.Instant.now())
                    .build());
            synthesize(session.getCallId(), msg);
        }

        /** Queue pre-encoded mu-law bytes onto the serial TTS chain so they
         *  ride the same epoch + counter + ordering machinery as live TTS.
         *  {@code startDelayMs} is honoured AFTER the task is dequeued and
         *  BEFORE the bot-speaking counter is bumped, so a natural pause
         *  separates the caller finishing their sentence from the filler
         *  starting. The epoch is re-checked after the delay — if the user
         *  barged in during that window, the filler is dropped. */
        private void queueRawAudio(byte[] mulawBytes, String tag, long startDelayMs,
                                   boolean countAsBotSpeaking) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) return;
            long epochAtSubmit = session.getTtsEpoch().get();
            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                if (!ws.isOpen()) return;
                if (session.getTtsEpoch().get() != epochAtSubmit) return;

                if (startDelayMs > 0) {
                    try { Thread.sleep(startDelayMs); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (!ws.isOpen()) return;
                    if (session.getTtsEpoch().get() != epochAtSubmit) {
                        log.info("[tts] SKIP {} callId={} epoch={} reason=epoch-bumped-during-delay (now={})",
                                tag, session.getCallId(), epochAtSubmit, session.getTtsEpoch().get());
                        return;
                    }
                }

                if (countAsBotSpeaking) {
                    int prevInFlight = session.getTtsInFlight().getAndIncrement();
                    if (prevInFlight == 0) {
                        long sinceLast = System.currentTimeMillis() - session.getLastTtsActivityMs();
                        if (sinceLast > BURST_GAP_MS) {
                            session.setBotSpeakingStartMs(System.currentTimeMillis());
                        }
                    }
                }
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    int frame = 160; // 20 ms mu-law @ 8 kHz
                    int sent = 0;
                    while (sent < mulawBytes.length) {
                        if (session.getTtsEpoch().get() != epochAtSubmit) break;
                        int n = Math.min(frame, mulawBytes.length - sent);
                        byte[] piece = java.util.Arrays.copyOfRange(mulawBytes, sent, sent + n);
                        if (countAsBotSpeaking) BargeInHandler.noteTtsChunkSent(session);
                        sendMediaChunk(ws, streamSid, b64, piece);
                        sent += n;
                    }
                    log.info("[tts] played {} bytes={}/{} epoch={} aborted={}",
                            tag, sent, mulawBytes.length, epochAtSubmit, sent < mulawBytes.length);
                } catch (Exception ex) {
                    log.warn("[twilio] {} send failed callId={}: {}",
                            tag, session.getCallId(), ex.getMessage());
                } finally {
                    if (countAsBotSpeaking) {
                        session.getTtsInFlight().decrementAndGet();
                        BargeInHandler.noteTtsChunkSent(session);
                    }
                }
            }, ttsExecutor).exceptionally(ex -> null));
        }

        @Override
        public void onAiReply(String callId, String text) {
            replyStartedForTurn.set(true);
            synthesize(callId, text);
        }

        @Override
        public void onAiReplyChunk(String callId, String deltaText) {
            if (deltaText == null || deltaText.isEmpty()) return;
            replyStartedForTurn.set(true);
            synthesize(callId, deltaText);
        }

        @Override
        public void onAiTransientFailure(String callId) {
            playTroubleFallback();
        }

        /** Queue text behind any in-flight TTS work for this call so audio
         *  frames are produced and sent strictly in arrival order. */
        private void synthesize(String callId, String text) {
            String streamSid = (String) session.getProviderAttributes().get("streamSid");
            WebSocketSession ws = (WebSocketSession) session.getProviderAttributes().get("ws");
            if (streamSid == null || ws == null || !ws.isOpen()) {
                log.warn("[tts] DROP callId={} reason=ws-not-ready streamSid={} wsOpen={}",
                        callId, streamSid, ws != null && ws.isOpen());
                return;
            }

            long epochAtSubmit = session.getTtsEpoch().get();
            log.debug("[tts] QUEUE callId={} epoch={} chars={} text=\"{}\"",
                    callId, epochAtSubmit, text.length(),
                    text.length() > 80 ? text.substring(0, 80) + "…" : text);

            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
                if (!ws.isOpen()) {
                    log.debug("[tts] SKIP callId={} epoch={} reason=ws-closed", callId, epochAtSubmit);
                    return;
                }
                if (session.getTtsEpoch().get() != epochAtSubmit) {
                    log.debug("[tts] SKIP callId={} epoch={} reason=epoch-bumped-pre (now={})",
                            callId, epochAtSubmit, session.getTtsEpoch().get());
                    return;
                }
                int prevInFlight = session.getTtsInFlight().getAndIncrement();
                if (prevInFlight == 0) {
                    // Bot just transitioned silent → speaking. If it's been
                    // silent for more than BURST_GAP_MS, this is a NEW burst
                    // (e.g. fresh reply turn) — reset the grace clock.
                    long sinceLast = System.currentTimeMillis() - session.getLastTtsActivityMs();
                    if (sinceLast > BURST_GAP_MS) {
                        session.setBotSpeakingStartMs(System.currentTimeMillis());
                    }
                }
                long start = System.currentTimeMillis();
                int[] totalBytes = {0};
                try {
                    Base64.Encoder b64 = Base64.getEncoder();
                    log.debug("[tts] START callId={} epoch={} (calling ElevenLabs)", callId, epochAtSubmit);
                    textToSpeechProvider.synthesizeStream(text,
                            VoiceProfile.builder().language(session.getLanguage()).build(),
                            chunk -> {
                                if (session.getTtsEpoch().get() != epochAtSubmit) {
                                    throw new BargeInAbortException();
                                }
                                BargeInHandler.noteTtsChunkSent(session);
                                totalBytes[0] += chunk.length;
                                sendMediaChunk(ws, streamSid, b64, chunk);
                            });
                    log.debug("[tts] DONE  callId={} epoch={} bytes={} totalMs={}",
                            callId, epochAtSubmit, totalBytes[0], System.currentTimeMillis() - start);
                } catch (BargeInAbortException bx) {
                    log.debug("[tts] ABORT callId={} epoch={} bytes={} reason=barge-in",
                            callId, epochAtSubmit, totalBytes[0]);
                } catch (Exception ex) {
                    log.warn("[tts] FAIL  callId={} epoch={} bytes={} reason={}",
                            callId, epochAtSubmit, totalBytes[0], ex.getMessage());
                } finally {
                    session.getTtsInFlight().decrementAndGet();
                    BargeInHandler.noteTtsChunkSent(session);
                    // First real TTS task to complete = greeting done. STT
                    // consumer can now start forwarding events.
                    if (session.getGreetingDone().compareAndSet(false, true)) {
                        log.info("[greeting] done — STT now active callId={}", callId);
                    }
                }
            }, ttsExecutor).exceptionally(ex -> null));
        }

        /** Sentinel to unwind the TTS streaming callback when the caller has
         *  started talking mid-reply. Caught by {@link #synthesize}; never
         *  escapes to the executor. */
        private final class BargeInAbortException extends RuntimeException {
            BargeInAbortException() { super(null, null, false, false); }
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

            // Chain the farewell off any in-flight TTS for this call so it
            // never races chunks from the prior turn. Then wait for the
            // farewell audio to actually play out on the caller's side
            // before terminating the call — Twilio buffers ~1s, and the
            // carrier adds another ~250ms, so we use a 1200ms tail.
            ttsTail.updateAndGet(prev -> prev.thenRunAsync(() -> {
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
                        long playbackMs = bytes[0] / AudioCodec.MULAW.bytesPerMs() + 1200L;
                        Thread.sleep(playbackMs);
                    }
                } catch (Exception ex) {
                    log.warn("[twilio] hangup TTS failed callId={}: {}", callId, ex.getMessage());
                }
                hangupTwilioCall(callId, twilioCallSid);
            }, ttsExecutor).exceptionally(ex -> { hangupTwilioCall(callId, twilioCallSid); return null; }));
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