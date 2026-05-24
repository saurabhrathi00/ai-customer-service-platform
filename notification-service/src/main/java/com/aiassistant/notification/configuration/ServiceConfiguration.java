package com.aiassistant.notification.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private AuthService authService;
    private UserBusinessService userBusinessService;
    private Whatsapp whatsapp;
    private Dashboard dashboard;
    private Scheduler scheduler;

    @Data
    public static class AuthService {
        private String baseUrl;
    }

    @Data
    public static class UserBusinessService {
        private String baseUrl;
    }

    /**
     * Meta WhatsApp Cloud API config. {@link #stubMode} short-circuits the
     * HTTP send for local dev — every "send" returns success and logs the
     * payload, so the rest of the pipeline can be tested without a Meta
     * account. Flip to false when the WABA + templates are live.
     */
    @Data
    public static class Whatsapp {
        private boolean stubMode;
        /** Meta Graph API base, e.g. {@code https://graph.facebook.com/v21.0}. */
        private String graphApiBaseUrl;
        /** Phone-number ID issued by Meta for the WABA's number. */
        private String phoneNumberId;
        /** Template names. Must match the names registered + approved on Meta. */
        private String ownerNewLeadTemplate;
        private String customerApptConfirmedTemplate;
        private String customerApptDeclinedTemplate;
        private String customerAgentWillConnectTemplate;
        /** Language code each template was registered under (e.g. "en"). */
        private String templateLanguage;
    }

    @Data
    public static class Dashboard {
        /** Public URL the WhatsApp template links to. The
         *  {@code {leadId}} placeholder is filled in per send. */
        private String leadLinkTemplate;
    }

    @Data
    public static class Scheduler {
        /** How often the scheduler wakes up to drain pending work. Cron
         *  expression (Spring style). Default every minute. */
        private String cron;
    }
}
