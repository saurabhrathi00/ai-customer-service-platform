package com.aiassistant.notification.whatsapp;

import com.aiassistant.notification.configuration.SecretsConfiguration;
import com.aiassistant.notification.configuration.ServiceConfiguration;
import com.aiassistant.notification.exceptions.DownstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Meta WhatsApp Cloud API client. Only sends pre-approved template
 * messages — that's the only path that works outside a 24-hour service
 * window, which is exactly our use case (outbound notifications, no
 * customer-initiated session).
 *
 * <p>{@link ServiceConfiguration.Whatsapp#isStubMode() stubMode=true} short-
 * circuits the HTTP send so the rest of the pipeline can be exercised
 * locally without a Meta account. Every "send" logs the rendered template
 * payload and returns success.</p>
 */
@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final RestClient whatsappRestClient;
    private final ServiceConfiguration serviceConfiguration;
    private final SecretsConfiguration secretsConfiguration;

    public WhatsAppClient(
            @Qualifier("whatsappRestClient") RestClient whatsappRestClient,
            ServiceConfiguration serviceConfiguration,
            SecretsConfiguration secretsConfiguration) {
        this.whatsappRestClient = whatsappRestClient;
        this.serviceConfiguration = serviceConfiguration;
        this.secretsConfiguration = secretsConfiguration;
    }

    /**
     * Send a templated message. {@code bodyParameters} are positional and
     * fill {@code {{1}}}, {@code {{2}}}, … placeholders in the registered
     * template. {@code buttonUrlSuffix} (nullable) fills the dynamic URL
     * placeholder on a single button if the template has one.
     */
    public void sendTemplate(String toPhoneE164,
                             String templateName,
                             List<String> bodyParameters,
                             String buttonUrlSuffix) {
        ServiceConfiguration.Whatsapp cfg = serviceConfiguration.getWhatsapp();
        if (cfg == null) {
            throw new IllegalStateException("WhatsApp config missing");
        }
        if (cfg.isStubMode()) {
            log.info("[wa-stub] template={} to={} params={} buttonSuffix={}",
                    templateName, redact(toPhoneE164), bodyParameters, buttonUrlSuffix);
            return;
        }
        if (cfg.getPhoneNumberId() == null || cfg.getPhoneNumberId().isBlank()) {
            throw new IllegalStateException("WhatsApp phoneNumberId is not configured");
        }
        if (secretsConfiguration.getWhatsapp() == null
                || secretsConfiguration.getWhatsapp().getAccessToken() == null) {
            throw new IllegalStateException("WhatsApp access token is not configured");
        }
        Map<String, Object> body = buildPayload(toPhoneE164, templateName,
                bodyParameters, buttonUrlSuffix, cfg.getTemplateLanguage());
        try {
            whatsappRestClient.post()
                    .uri("/{phoneNumberId}/messages", cfg.getPhoneNumberId())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + secretsConfiguration.getWhatsapp().getAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[wa] sent template={} to={}", templateName, redact(toPhoneE164));
        } catch (RestClientException ex) {
            throw new DownstreamServiceException(
                    "WhatsApp send failed (template=" + templateName + "): " + ex.getMessage(), ex);
        }
    }

    private static Map<String, Object> buildPayload(String to,
                                                    String templateName,
                                                    List<String> bodyParameters,
                                                    String buttonUrlSuffix,
                                                    String language) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("messaging_product", "whatsapp");
        root.put("to", to);
        root.put("type", "template");

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", language == null ? "en" : language));

        List<Map<String, Object>> components = new ArrayList<>();
        if (bodyParameters != null && !bodyParameters.isEmpty()) {
            Map<String, Object> bodyComp = new LinkedHashMap<>();
            bodyComp.put("type", "body");
            bodyComp.put("parameters", bodyParameters.stream()
                    .map(p -> Map.<String, Object>of("type", "text", "text", p == null ? "" : p))
                    .toList());
            components.add(bodyComp);
        }
        if (buttonUrlSuffix != null && !buttonUrlSuffix.isBlank()) {
            // Meta's URL-button template parameter — fills the dynamic suffix on
            // a CTA URL button (the static base lives in the registered template).
            Map<String, Object> btn = new LinkedHashMap<>();
            btn.put("type", "button");
            btn.put("sub_type", "url");
            btn.put("index", "0");
            btn.put("parameters", List.of(
                    Map.of("type", "text", "text", buttonUrlSuffix)));
            components.add(btn);
        }
        if (!components.isEmpty()) {
            template.put("components", components);
        }
        root.put("template", template);
        return root;
    }

    private static String redact(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
