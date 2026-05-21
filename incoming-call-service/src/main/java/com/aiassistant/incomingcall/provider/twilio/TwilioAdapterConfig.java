package com.aiassistant.incomingcall.provider.twilio;

import com.twilio.security.RequestValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adapter-owned configuration. Reads secrets.twilio.* and exposes the
 * Twilio SDK's RequestValidator for use inside this adapter only.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "secrets.twilio")
public class TwilioAdapterConfig {

    private String accountSid;
    private String authToken;

    @Bean
    public RequestValidator twilioRequestValidator() {
        return new RequestValidator(authToken);
    }
}
