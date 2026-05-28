package com.aiassistant.incomingcall.provider.enablex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "secrets.enablex", name = "appId")
public class EnableXApiClient {

    private static final Logger log = LoggerFactory.getLogger(EnableXApiClient.class);

    private final RestClient enablexRestClient;

    public EnableXApiClient(@Qualifier("enablexRestClient") RestClient enablexRestClient) {
        this.enablexRestClient = enablexRestClient;
    }

    public void acceptCall(String voiceId) {
        log.info("Accepting EnableX call voiceId={}", voiceId);
        enablexRestClient.put()
                .uri("/voice/v1/call/{voiceId}/accept", voiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .body("")
                .retrieve()
                .toBodilessEntity();
        log.info("EnableX call accepted voiceId={}", voiceId);
    }

    public void startStream(String voiceId, String wssHost) {
        log.info("Starting EnableX stream voiceId={} wssHost={}", voiceId, wssHost);
        enablexRestClient.put()
                .uri("/voice/v1/call/{voiceId}/stream", voiceId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("wss_host", wssHost))
                .retrieve()
                .toBodilessEntity();
        log.info("EnableX stream started voiceId={} wssHost={}", voiceId, wssHost);
    }
}
