package com.aiassistant.userbusiness.configuration;

import com.aiassistant.userbusiness.models.error.ApiError;
import com.aiassistant.userbusiness.security.JwtAuthenticationFilter;
import com.aiassistant.userbusiness.security.token.TokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenProvider tokenProvider) {
        return new JwtAuthenticationFilter(tokenProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           ObjectMapper objectMapper) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // health + business registration are public
                        .requestMatchers(HttpMethod.GET, "/api/v1/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/business/register").permitAll()
                        // tenant-facing endpoints
                        .requestMatchers("/api/v1/business/**").authenticated()
                        // service-to-service
                        .requestMatchers("/api/internal/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jsonAuthEntryPoint(objectMapper))
                        .accessDeniedHandler(jsonAccessDeniedHandler(objectMapper))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private AuthenticationEntryPoint jsonAuthEntryPoint(ObjectMapper mapper) {
        return (request, response, authException) ->
                writeError(response, mapper, HttpStatus.UNAUTHORIZED, authException);
    }

    private AccessDeniedHandler jsonAccessDeniedHandler(ObjectMapper mapper) {
        return (request, response, accessDeniedException) ->
                writeError(response, mapper, HttpStatus.FORBIDDEN, accessDeniedException);
    }

    private void writeError(HttpServletResponse response,
                            ObjectMapper mapper,
                            HttpStatus status,
                            Exception ex) throws java.io.IOException {
        String message = status == HttpStatus.UNAUTHORIZED
                ? "Authentication required"
                : "Access denied";
        if (ex instanceof AuthenticationException || ex instanceof AccessDeniedException) {
            if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
                message = ex.getMessage();
            }
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), new ApiError(status, message));
    }
}
