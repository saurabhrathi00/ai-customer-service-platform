package com.aiassistant.callorchestration.telephony.enablex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(prefix = "secrets.enablex", name = "appId")
public class EnableXApiClient {

    private static final Logger log = LoggerFactory.getLogger(EnableXApiClient.class);

    private final RestClient enablexRestClient;

    public EnableXApiClient(@Qualifier("enablexRestClient") RestClient enablexRestClient) {
        this.enablexRestClient = enablexRestClient;
    }

    public void hangupCall(String voiceId) {
        log.info("[enablex-api] hanging up voiceId={}", voiceId);
        try {
            enablexRestClient.delete()
                    .uri("/voice/v1/call/{voiceId}", voiceId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[enablex-api] call hung up voiceId={}", voiceId);
        } catch (Exception ex) {
            log.warn("[enablex-api] hangup failed voiceId={}: {}", voiceId, ex.getMessage());
        }
    }
}
