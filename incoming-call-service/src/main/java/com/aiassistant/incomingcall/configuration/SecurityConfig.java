package com.aiassistant.incomingcall.configuration;

import com.aiassistant.incomingcall.provider.TelephonyProviderRegistry;
import com.aiassistant.incomingcall.security.TelephonySignatureFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public TelephonySignatureFilter telephonySignatureFilter(TelephonyProviderRegistry registry) {
        return new TelephonySignatureFilter(registry);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TelephonySignatureFilter telephonySignatureFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health").permitAll()
                        // Webhook routes: per-provider signature filter authenticates; Spring permits.
                        .requestMatchers("/api/v1/webhook/**").permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(telephonySignatureFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
