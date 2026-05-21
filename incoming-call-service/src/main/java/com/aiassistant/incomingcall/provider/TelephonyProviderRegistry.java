package com.aiassistant.incomingcall.provider;

import com.aiassistant.incomingcall.provider.TelephonyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Discovers all TelephonyProvider beans on the classpath and indexes them by name().
 * Adding a new provider = ship a new adapter module; this registry picks it up automatically.
 */
@Component
public class TelephonyProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(TelephonyProviderRegistry.class);

    private final Map<String, TelephonyProvider> providersByName;

    public TelephonyProviderRegistry(List<TelephonyProvider> providers) {
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        p -> p.name().toLowerCase(Locale.ROOT),
                        Function.identity()));
        log.info("Telephony providers discovered: {}", providersByName.keySet());
    }

    public Optional<TelephonyProvider> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(providersByName.get(name.toLowerCase(Locale.ROOT)));
    }
}
