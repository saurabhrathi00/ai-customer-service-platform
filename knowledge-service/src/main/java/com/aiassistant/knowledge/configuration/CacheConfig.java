package com.aiassistant.knowledge.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    public static final String CACHE_RENDERED = "rendered-knowledge";

    private final ServiceConfiguration serviceConfiguration;

    @Bean
    public CacheManager cacheManager() {
        ServiceConfiguration.Rendered cfg = serviceConfiguration.getCache().getRendered();
        CaffeineCacheManager mgr = new CaffeineCacheManager(CACHE_RENDERED);
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(cfg.getTtlMinutes(), TimeUnit.MINUTES)
                .maximumSize(cfg.getMaxEntries()));
        return mgr;
    }
}
