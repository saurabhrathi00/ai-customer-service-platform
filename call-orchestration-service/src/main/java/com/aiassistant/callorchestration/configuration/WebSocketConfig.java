package com.aiassistant.callorchestration.configuration;

import com.aiassistant.callorchestration.security.TelephonyHandshakeInterceptor;
import com.aiassistant.callorchestration.telephony.MediaStreamDispatcherHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final MediaStreamDispatcherHandler mediaStreamDispatcherHandler;
    private final TelephonyHandshakeInterceptor telephonyHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(mediaStreamDispatcherHandler, "/ws/*/call/*")
                .addInterceptors(telephonyHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}