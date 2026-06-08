package com.aiassistant.callorchestration.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private BusinessDb businessDb;
    private AuthService authService;
    private DownstreamService userBusinessService;
    private DownstreamService knowledgeService;
    private AiConversationService aiConversationService;
    private DownstreamService conversationSummaryService;
    private DownstreamService notificationService;
    private DownstreamService subscriptionService;
    private Stt stt;
    private Tts tts;
    private ElevenLabs elevenlabs;
    private Deepgram deepgram;
    private Filler filler = new Filler();
    private Silence silence = new Silence();
    private BargeIn bargeIn = new BargeIn();
    private Recording recording = new Recording();
    private EnableXApi enablexApi;

    @Data
    public static class Recording {
        private boolean enabled = false;
    }

    @Data
    public static class EnableXApi {
        private String baseUrl;
    }

    @Data
    public static class BargeIn {
        private boolean enabled = true;
        /** Minimum characters in the STT transcript to trigger barge-in.
         *  Filters out noise / tiny partial artifacts. */
        private int minTextLength = 4;
        /** Minimum ms between successive barge-in triggers for the same call. */
        private long debounceMs = 300;
        /** Don't barge if bot started speaking less than this many ms ago.
         *  Lets the caller hear at least the opening of the response. */
        private long gracePeriodMs = 500;
        /** If bot has less than this many ms of audio left, let it finish
         *  rather than cutting off the tail end. */
        private long remainingAudioThresholdMs = 600;
        /** Max ms of audio allowed in the carrier buffer. Audio is drip-fed
         *  so the buffer never exceeds this — on barge-in, at most this much
         *  audio plays before the bot stops. 0 = no pacing (send as fast as
         *  TTS produces). */
        private long maxBufferMs = 800;
        /** How often (ms) to drip the next chunk of audio into the carrier
         *  buffer. Should be less than maxBufferMs to prevent gaps. */
        private long dripIntervalMs = 400;
        /** Enable two-stage barge-in: pause on partial, confirm/resume on final. */
        private boolean twoStageEnabled = true;
        /** Min chars in partial to trigger Stage 1 pause. */
        private int partialMinTextLength = 6;
        /** Min words in partial to trigger Stage 1 pause. */
        private int partialMinWordCount = 2;
        /** Min chars in partial for immediate full barge-in (skip Stage 2). */
        private int immediateMinTextLength = 15;
        /** Min words in partial for immediate full barge-in (skip Stage 2). */
        private int immediateMinWordCount = 3;
        /** Max ms to stay paused waiting for a final before auto-resuming. */
        private long pauseTimeoutMs = 3000;
    }

    @Data
    public static class Silence {
        /** Master switch — when false, no nudge or silence-hangup ever fires. */
        private boolean enabled = true;
        /** After this many ms of total caller silence, play a gentle nudge
         *  ("Hello, are you there?"). The clock anchors to the latest of:
         *  call start, last STT event, or bot finishing speaking. */
        private long nudgeAfterMs = 12000;
        /** After this many ms of continued silence (since the nudge),
         *  give up and terminate the call with a polite goodbye. */
        private long hangupAfterNudgeMs = 13000;
        /** Watchdog check interval. Smaller = snappier reactions, more CPU. */
        private long checkIntervalMs = 2000;
        /** Nudge prompts — language is picked from session.getLanguage()
         *  if set, else bilingual fallback. */
        private String nudgeTextEn = "Hello, are you still there?";
        private String nudgeTextHi = "Hello, kya aap line par hain?";
        /** Farewell played right before silence-triggered hangup. */
        private String farewellTextEn = "It seems the line went quiet. I'll end the call now. Have a great day!";
        private String farewellTextHi = "Lagta hai line saaf nahi hai. Main call abhi end kar raha hoon. Aapka din shubh ho!";
    }

    @Data
    public static class Filler {
        /** Master switch — when false, no filler audio is ever played. */
        private boolean enabled = false;
        /** Filler phrases for English callers. Picked round-robin per call. */
        private java.util.List<String> phrasesEn = java.util.List.of(
                "One moment.", "Let me check.", "Sure, one sec.");
        /** Filler phrases for Hindi/Hinglish callers. */
        private java.util.List<String> phrasesHi = java.util.List.of(
                "Ek second.", "Haan ji, dekhta hoon.", "Theek hai, ek minute.");
        /** Acknowledgement fillers — played when the caller said a STATEMENT
         *  (not a question). "Let me check" sounds wrong after "I need a
         *  quote for my warehouse"; "Got it" / "Noted" sound natural. */
        // Keep these to a single short token. They follow ANY statement
        // ("I want a quote", "my warehouse is in Pune", "I'll think about
        // it") and must sound natural without referencing content. Anything
        // longer ("Got it, noted") starts to feel scripted on the 3rd reuse.
        private java.util.List<String> ackPhrasesEn = java.util.List.of(
                "Okay.", "Hmm.", "Right.", "Sure.");
        private java.util.List<String> ackPhrasesHi = java.util.List.of(
                "Achha.", "Hmm.", "Theek.", "Haan ji.");
        /** Don't play a filler if we already played one within this many ms
         *  — avoids "ek sec... ek sec..." stuttering on short back-to-back
         *  turns. */
        private long minGapMs = 4000;
        /** Wait this many ms after the caller stops speaking before the
         *  filler audio actually starts. Small natural pause — the bot
         *  "registers" the question and then says "ek sec…" instead of
         *  jumping in instantly. ~700-1000 ms feels right; anything more
         *  and the silence becomes awkward. */
        private long startDelayMs = 800;
        /** Don't play a filler for utterances shorter than this. Short
         *  acks like "yes", "ok", "thanks", "haan" don't need a "let me
         *  check" — that just adds dead air to a turn the LLM will
         *  answer in <1 second anyway. */
        private int minUtteranceChars = 25;
    }

    @Data
    public static class BusinessDb {
        private String name;
        private String schema;
        private String url;
        private Pool pool;
    }

    @Data
    public static class Pool {
        private int maximumPoolSize;
        private int minimumIdle;
        private long maxLifetimeMs;
        private long idleTimeoutMs;
        private long keepaliveTimeMs;
        private String connectionTestQuery;
    }

    @Data
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class DownstreamService {
        private String baseUrl;
    }

    @Data
    public static class AiConversationService {
        /** REST base — kept for non-WS endpoints (e.g. summarise). */
        private String baseUrl;
        /** WebSocket base, e.g. {@code ws://host:8087/ai-conversation-service/ws/conversation}.
         *  The conversationId path segment is appended at connect time. */
        private String wsBaseUrl;
        /** Optional provider override sent on INIT (blank → server default). */
        private String provider;
        private int connectTimeoutMs;
        private int sendTimeoutMs;
    }

    @Data
    public static class Stt {
        private String provider;
        /** Transcripts below this confidence are sent as UNCLEAR_MESSAGE
         *  instead of MESSAGE — ai-conv responds with a canned "please repeat"
         *  reply (no LLM round-trip). */
        private Double confidenceThreshold = 0.5;
        private Double highConfidenceThreshold = 0.9;
        /** When true, Devanagari script in STT output is transliterated to
         *  Latin (Romanised Hindi). When false, the original script is
         *  passed through as-is. */
        private boolean transliterateDevanagari = true;
    }

    @Data
    public static class Tts {
        private String provider;
    }

    @Data
    public static class Deepgram {
        /** Flux model id, e.g. {@code flux-general-en} or {@code flux-general-multi}. */
        private String sttModelId = "flux-general-en";
        /** Force end-of-turn after this many ms of silence (default Deepgram
         *  value is 5000; we want faster commit). */
        private Integer sttEotTimeoutMs = 400;
        /** Nova model id used when {@code configs.stt.provider=deepgram-nova},
         *  e.g. {@code nova-3-general}. */
        private String novaModelId = "nova-3-general";
        /** Nova language code or {@code multi} for code-switching. */
        private String novaLanguage = "multi";
        /** Server-side silence (ms) that triggers a final commit on Nova.
         *  Smaller = snappier but riskier for mid-sentence pauses. */
        private Integer novaEndpointingMs = 400;
    }


    @Data
    public static class ElevenLabs {
        private String sttWsUrl;
        private String sttModelId;
        private String sttLanguageCode;
        private boolean sttIncludeLanguageDetection;
        private String sttCommitStrategy;
        private boolean sttNoVerbatim;
        /** With {@code sttCommitStrategy=manual}, ElevenLabs never auto-commits;
         *  we send a {@code commit} message ourselves after this many milliseconds
         *  of silence (no incoming partial transcript). Default ~400 ms keeps
         *  perceived latency low. */
        private Long sttManualCommitSilenceMs;
        /** With {@code sttCommitStrategy=vad}, ElevenLabs auto-commits when
         *  it detects this many seconds of silence after speech. Default 1.5.
         *  Lower = snappier turn-taking, but risks chopping mid-sentence
         *  pauses. */
        private Double sttVadSilenceThresholdSecs;
        /** VAD speech-detection threshold, 0–1. Lower = more sensitive
         *  (catches softer speech, but also more background noise).
         *  Default 0.4. */
        private Double sttVadThreshold;

        private String ttsBaseUrl;
        private String ttsVoiceId;
        private String ttsModelId;
        private String ttsOutputFormat;
        private Double ttsStability;
        private Double ttsSimilarityBoost;
        private Double ttsStyle;
        private Boolean ttsUseSpeakerBoost;
    }
}
