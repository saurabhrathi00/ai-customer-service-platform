package com.aiassistant.callorchestration.voice;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pre-synthesises a small set of "thinking" phrases ("ek sec...", "one
 * moment...") at startup so a call can play one INSTANTLY when a customer
 * utterance arrives — no TTS round-trip during the call. The real LLM
 * reply is queued behind it on the per-call serial TTS chain and starts
 * playing as soon as the filler finishes.
 *
 * <p>Audio is cached on disk after the first synthesis in a folder structure:
 * {@code /app/filler-cache/{voiceId}/{language}/{category}_{index}.mulaw}.
 * Subsequent startups load from disk — no TTS API calls unless the cache
 * is missing or the voice/phrases changed.
 *
 * <p>Disabled by default. Flip {@code configs.filler.enabled=true} to
 * activate. Phrases and the minimum gap between fillers are configurable
 * under {@code configs.filler.*}.
 */
@Component
@RequiredArgsConstructor
public class FillerAudioCache {

    private static final Logger log = LoggerFactory.getLogger(FillerAudioCache.class);
    private static final Path BASE_CACHE_DIR = Path.of("/app/filler-cache");

    private final ServiceConfiguration configs;
    private final TextToSpeechProvider tts;

    private List<byte[]> enClips = List.of();
    private List<byte[]> hiClips = List.of();
    private List<byte[]> enAckClips = List.of();
    private List<byte[]> hiAckClips = List.of();

    @PostConstruct
    void preSynthesise() {
        ServiceConfiguration.Filler cfg = configs.getFiller();
        if (cfg == null || !cfg.isEnabled()) {
            log.info("[filler] disabled — skipping pre-synthesis");
            return;
        }

        String voiceId = configs.getElevenlabs().getTtsVoiceId();
        if (voiceId == null || voiceId.isBlank()) voiceId = "default";

        enClips    = loadOrSynth(cfg.getPhrasesEn(), "en", "thinking", voiceId);
        hiClips    = loadOrSynth(cfg.getPhrasesHi(), "hi", "thinking", voiceId);
        enAckClips = loadOrSynth(cfg.getAckPhrasesEn(), "en", "ack", voiceId);
        hiAckClips = loadOrSynth(cfg.getAckPhrasesHi(), "hi", "ack", voiceId);
        log.info("[filler] cached thinking: en={} hi={} | ack: en={} hi={}",
                enClips.size(), hiClips.size(), enAckClips.size(), hiAckClips.size());
    }

    private List<byte[]> loadOrSynth(List<String> phrases, String lang, String category, String voiceId) {
        List<byte[]> out = new ArrayList<>();
        if (phrases == null) return out;

        Path dir = BASE_CACHE_DIR.resolve(voiceId).resolve(lang);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("[filler] cannot create cache dir {}: {}", dir, e.getMessage());
        }

        for (int i = 0; i < phrases.size(); i++) {
            String p = phrases.get(i);
            if (p == null || p.isBlank()) continue;

            Path cached = dir.resolve(category + "_" + i + ".mulaw");
            byte[] mulaw = loadFromDisk(cached);
            if (mulaw != null) {
                out.add(mulaw);
                continue;
            }
            try {
                mulaw = tts.synthesize(p, VoiceProfile.builder().language(lang).build());
                if (mulaw != null && mulaw.length > 0) {
                    out.add(mulaw);
                    saveToDisk(cached, mulaw);
                }
            } catch (Exception ex) {
                log.warn("[filler] failed to synthesise \"{}\" ({}): {}",
                        p, lang, ex.getMessage());
            }
        }
        return out;
    }

    private byte[] loadFromDisk(Path path) {
        if (!Files.exists(path)) return null;
        try {
            byte[] data = Files.readAllBytes(path);
            if (data.length > 0) {
                log.debug("[filler] loaded from disk: {}", path);
                return data;
            }
        } catch (IOException e) {
            log.warn("[filler] failed to read cache file {}: {}", path, e.getMessage());
        }
        return null;
    }

    private void saveToDisk(Path path, byte[] data) {
        try {
            Files.write(path, data);
            log.debug("[filler] saved to disk: {}", path);
        } catch (IOException e) {
            log.warn("[filler] failed to write cache file {}: {}", path, e.getMessage());
        }
    }

    public boolean isEnabled() {
        ServiceConfiguration.Filler cfg = configs.getFiller();
        if (cfg == null || !cfg.isEnabled()) return false;
        return !(enClips.isEmpty() && hiClips.isEmpty()
                && enAckClips.isEmpty() && hiAckClips.isEmpty());
    }

    public long getMinGapMs() {
        ServiceConfiguration.Filler cfg = configs.getFiller();
        return cfg == null ? 4000L : cfg.getMinGapMs();
    }

    public long getStartDelayMs() {
        ServiceConfiguration.Filler cfg = configs.getFiller();
        return cfg == null ? 800L : cfg.getStartDelayMs();
    }

    public int getMinUtteranceChars() {
        ServiceConfiguration.Filler cfg = configs.getFiller();
        return cfg == null ? 25 : cfg.getMinUtteranceChars();
    }

    public byte[] pickForText(String text) {
        return pickFromPool(text, false);
    }

    public byte[] pickAckForText(String text) {
        return pickFromPool(text, true);
    }

    private byte[] pickFromPool(String text, boolean ack) {
        boolean isHindi = looksHindi(text);
        List<byte[]> pool;
        if (ack) pool = isHindi ? hiAckClips : enAckClips;
        else     pool = isHindi ? hiClips    : enClips;
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static final java.util.Set<String> HINDI_TOKENS = java.util.Set.of(
            "haan", "nahi", "nahin", "kya", "kaise", "kahan", "kab", "kyun",
            "kyon", "kyu", "kaisa", "kaisi",
            "bhai", "didi", "ji", "theek", "thik", "batao", "bataiye",
            "chahiye", "chahta", "chahti",
            "karna", "karo", "karta", "karti", "karte", "kiya", "kiye",
            "hona", "hai", "hain", "hoon", "raha", "rahi", "rahe",
            "mujhe", "tumhe", "aapko", "humein",
            "aap", "tum", "hum", "main", "mera", "meri", "mere",
            "tera", "teri", "tere", "humara", "humari", "aapka", "aapki",
            "namaste", "namaskar", "shukriya", "dhanyavaad", "matlab",
            "samajh", "samjha", "samjhi", "samjhe",
            "achha", "acha", "achchha", "abhi", "phir", "lekin", "magar",
            "kyunki", "kyonki", "isliye",
            "wala", "wali", "wale", "wahi", "yahi", "yeh", "woh", "vahi",
            "milega", "milegi", "kha", "khao", "kuch", "koi",
            "thoda", "thodi", "zyada", "kam");

    public static boolean looksHindi(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c >= 'ऀ' && c <= 'ॿ') return true;
        }
        java.util.Set<String> hits = new java.util.HashSet<>();
        for (String tok : lower.split("[^a-zA-Z]+")) {
            if (HINDI_TOKENS.contains(tok)) {
                hits.add(tok);
                if (hits.size() >= 2) return true;
            }
        }
        return false;
    }
}
