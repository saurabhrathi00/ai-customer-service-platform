package com.aiassistant.subscription.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "configs")
public class ServiceConfiguration {

    private SubscriptionDb subscriptionDb;
    private DownstreamService authService;
    private DownstreamService userBusinessService;
    private Gst gst;
    private RazorpayConfig razorpay;

    @Data
    public static class SubscriptionDb {
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
    }

    @Data
    public static class DownstreamService {
        private String baseUrl;
    }

    @Data
    public static class Gst {
        private int ratePercent;
    }

    @Data
    public static class RazorpayConfig {
        private int webhookToleranceSeconds;
    }
}
