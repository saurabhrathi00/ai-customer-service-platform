package com.aiassistant.aiconversation.llm.gemini;

import com.aiassistant.aiconversation.configuration.SecretsConfiguration;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.util.List;

/**
 * Loads the GCP service-account JSON once at startup and exposes a method to
 * mint a fresh OAuth2 access token for the Vertex AI scope. The underlying
 * {@link GoogleCredentials} object caches tokens and refreshes them about 5
 * minutes before they expire, so callers can ask for a token on every request
 * without paying a network cost.
 *
 * <p>Vertex AI is disabled when {@code secrets.llm.gemini.vertex.enabled=false}.
 * In that case this bean is still constructed but {@link #isEnabled()} returns
 * false; the legacy AI Studio path stays in use.
 */
@Component
@RequiredArgsConstructor
public class VertexAiTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(VertexAiTokenProvider.class);
    private static final String SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    private final SecretsConfiguration secrets;
    private volatile GoogleCredentials credentials;

    @PostConstruct
    void init() {
        SecretsConfiguration.Vertex v = vertexCfg();
        if (v == null || !v.isEnabled()) {
            log.info("[vertex] disabled (secrets.llm.gemini.vertex.enabled=false)");
            return;
        }
        if (v.getCredentialsPath() == null || v.getCredentialsPath().isBlank()) {
            log.warn("[vertex] enabled=true but credentialsPath is blank — disabling");
            return;
        }
        try (FileInputStream in = new FileInputStream(v.getCredentialsPath())) {
            this.credentials = GoogleCredentials.fromStream(in)
                    .createScoped(List.of(SCOPE));
            log.info("[vertex] credentials loaded projectId={} region={}",
                    v.getProjectId(), v.getRegion());
        } catch (Exception e) {
            log.error("[vertex] failed to load credentials from {}: {}",
                    v.getCredentialsPath(), e.getMessage());
        }
    }

    public boolean isEnabled() {
        return credentials != null;
    }

    public String projectId() {
        SecretsConfiguration.Vertex v = vertexCfg();
        return v == null ? null : v.getProjectId();
    }

    public String region() {
        SecretsConfiguration.Vertex v = vertexCfg();
        return v == null ? null : v.getRegion();
    }

    /** Vertex AI base URL for the configured region, e.g.
     *  {@code https://asia-south1-aiplatform.googleapis.com}. */
    public String baseUrl() {
        return "https://" + region() + "-aiplatform.googleapis.com";
    }

    /** Fresh access token. The library refreshes automatically; this call is
     *  ~free when the cached token is still valid. */
    public String accessToken() {
        if (credentials == null) {
            throw new IllegalStateException("Vertex AI credentials not configured");
        }
        try {
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            throw new RuntimeException("Failed to mint Vertex access token: " + e.getMessage(), e);
        }
    }

    private SecretsConfiguration.Vertex vertexCfg() {
        if (secrets.getLlm() == null || secrets.getLlm().getGemini() == null) return null;
        return secrets.getLlm().getGemini().getVertex();
    }
}
