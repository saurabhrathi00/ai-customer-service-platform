package com.aiassistant.summary.llm;

import com.aiassistant.summary.configuration.ServiceConfiguration;
import com.aiassistant.summary.exceptions.AppException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LlmProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderRegistry.class);

    private final List<LlmProvider> providers;
    private final ServiceConfiguration serviceConfiguration;

    private final Map<String, LlmProvider> byId = new HashMap<>();

    @PostConstruct
    void init() {
        for (LlmProvider p : providers) {
            byId.put(p.id().toLowerCase(), p);
            log.info("Registered LLM provider: {}", p.id());
        }
        if (byId.isEmpty()) {
            log.warn("No LLM providers registered");
        }
    }

    public LlmProvider get(String id) {
        String key = (id == null || id.isBlank())
                ? serviceConfiguration.getLlm().getDefaultProvider()
                : id;
        LlmProvider p = byId.get(key == null ? null : key.toLowerCase());
        if (p == null) {
            throw new AppException("LLM provider not configured: " + key);
        }
        return p;
    }

    public LlmProvider getDefault() {
        return get(null);
    }
}
