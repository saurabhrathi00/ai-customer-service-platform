package com.aiassistant.callorchestration.telephony;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TelephonyMediaStreamHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(TelephonyMediaStreamHandlerRegistry.class);

    private final Map<String, TelephonyMediaStreamHandler> handlersById;

    public TelephonyMediaStreamHandlerRegistry(List<TelephonyMediaStreamHandler> handlers) {
        this.handlersById = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        h -> h.providerId().toLowerCase(Locale.ROOT),
                        Function.identity()));
        log.info("Telephony media stream handlers discovered: {}", handlersById.keySet());
    }

    public Optional<TelephonyMediaStreamHandler> find(String providerId) {
        if (providerId == null) return Optional.empty();
        return Optional.ofNullable(handlersById.get(providerId.toLowerCase(Locale.ROOT)));
    }
}
