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
    private Stt stt;
    private Tts tts;
    private ElevenLabs elevenlabs;
    private Deepgram deepgram;
    private Telephony telephony;

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
        /** Pause STT input while the bot is speaking — prevents echo/self-hearing. */
        private boolean muteWhileBotSpeaking = true;
        /** Minimum length (chars) of an interim transcript before it may
         *  trigger barge-in. Final transcripts always qualify regardless. */
        private int bargeInMinLengthChars = 12;
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
    }

    @Data
    public static class Telephony {
        /** Extra tail time (ms) appended to the estimated playback duration when
         *  hanging up the call. Ensures the farewell sentence finishes in the
         *  caller's ear before Twilio drops. */
        private long hangupTailMs = 500;
        /** Minimum playback duration to schedule after a TTS turn — protects
         *  against tiny one-word replies where bytes/8 is too short. */
        private long minPlaybackMs = 200;
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
