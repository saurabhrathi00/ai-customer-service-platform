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
        private String audience;
        private String scopes;
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
    }

    @Data
    public static class Tts {
        private String provider;
    }

    @Data
    public static class ElevenLabs {
        private String sttWsUrl;
        private String sttModelId;
        private String sttLanguageCode;
        private boolean sttIncludeLanguageDetection;
        private String sttCommitStrategy;
        private boolean sttNoVerbatim;
    }
}
