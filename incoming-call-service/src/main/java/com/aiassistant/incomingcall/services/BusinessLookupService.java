package com.aiassistant.incomingcall.services;

import com.aiassistant.incomingcall.clients.UserBusinessClient;
import com.aiassistant.incomingcall.exceptions.BusinessNotFoundException;
import com.aiassistant.incomingcall.models.response.BusinessLookupResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class BusinessLookupService {

    private static final Logger log = LoggerFactory.getLogger(BusinessLookupService.class);

    private final UserBusinessClient userBusinessClient;

    @Value("${configs.businessLookupCache.ttlSeconds:300}")
    private long ttlSeconds;

    @Value("${configs.businessLookupCache.negativeTtlSeconds:60}")
    private long negativeTtlSeconds;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public BusinessLookupResponse lookupByPhoneNumber(String phoneNumber) {
        CacheEntry entry = cache.get(phoneNumber);
        Instant now = Instant.now();
        if (entry != null && now.isBefore(entry.expiresAt)) {
            if (entry.notFound) {
                throw new BusinessNotFoundException("No business for " + phoneNumber + " (cached)");
            }
            return entry.value;
        }

        try {
            BusinessLookupResponse value = userBusinessClient.lookupByPhoneNumber(phoneNumber);
            cache.put(phoneNumber, CacheEntry.hit(value, now.plus(Duration.ofSeconds(ttlSeconds))));
            return value;
        } catch (BusinessNotFoundException ex) {
            cache.put(phoneNumber, CacheEntry.miss(now.plus(Duration.ofSeconds(negativeTtlSeconds))));
            throw ex;
        }
    }

    private record CacheEntry(BusinessLookupResponse value, boolean notFound, Instant expiresAt) {
        static CacheEntry hit(BusinessLookupResponse v, Instant exp) { return new CacheEntry(v, false, exp); }
        static CacheEntry miss(Instant exp) { return new CacheEntry(null, true, exp); }
    }
}