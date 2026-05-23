package com.aiassistant.callorchestration.voice;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
 * <p>Disabled by default. Flip {@code configs.filler.enabled=true} to
 * activate. Phrases and the minimum gap between fillers are configurable
 * under {@code configs.filler.*}.
 */
@Component
@RequiredArgsConstructor
public class FillerAudioCache {

    private static final Logger log = LoggerFactory.getLogger(FillerAudioCache.class);

    private final ServiceConfiguration configs;
    private final TextToSpeechProvider tts;

    /** Pre-encoded mu-law @ 8 kHz, ready to chunk into Twilio frames.
     *  Two pools per language: "thinking" (for questions) and "ack" (for
     *  statements). Statement ack clips skip the "let me check" framing
     *  which sounds wrong after a declarative utterance. */
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
        enClips    = synth(cfg.getPhrasesEn(), "en");
        hiClips    = synth(cfg.getPhrasesHi(), "hi");
        enAckClips = synth(cfg.getAckPhrasesEn(), "en");
        hiAckClips = synth(cfg.getAckPhrasesHi(), "hi");
        log.info("[filler] cached thinking: en={} hi={} | ack: en={} hi={}",
                enClips.size(), hiClips.size(), enAckClips.size(), hiAckClips.size());
    }

    private List<byte[]> synth(List<String> phrases, String lang) {
        List<byte[]> out = new ArrayList<>();
        if (phrases == null) return out;
        for (String p : phrases) {
            if (p == null || p.isBlank()) continue;
            try {
                byte[] mulaw = tts.synthesize(p,
                        VoiceProfile.builder().language(lang).build());
                if (mulaw != null && mulaw.length > 0) out.add(mulaw);
            } catch (Exception ex) {
                log.warn("[filler] failed to synthesise \"{}\" ({}): {}",
                        p, lang, ex.getMessage());
            }
        }
        return out;
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

    /** Pick a random clip in the language the caller actually used. NO
     *  cross-language fallback — if we can't match, we play nothing rather
     *  than answering a Hindi caller in English (and vice versa). Returns
     *  {@code null} when no matching clip is cached. */
    public byte[] pickForText(String text) {
        return pickFromPool(text, /*ack=*/false);
    }

    /** Pick a short acknowledgement clip ("Got it", "Samjh gaya") — used
     *  when the caller's utterance was a statement, not a question. */
    public byte[] pickAckForText(String text) {
        return pickFromPool(text, /*ack=*/true);
    }

    private byte[] pickFromPool(String text, boolean ack) {
        boolean isHindi = looksHindi(text);
        List<byte[]> pool;
        if (ack) pool = isHindi ? hiAckClips : enAckClips;
        else     pool = isHindi ? hiClips    : enClips;
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    /** Hindi-only tokens. Words that collide with English ("to", "se",
     *  "ka", "ke", "do", "or") are deliberately EXCLUDED — they trigger
     *  false positives on plain English sentences (e.g. "I want TO know"
     *  was misclassified as Hindi and got a Hindi filler). */
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

    /** Returns true only when the text contains Devanagari OR at least
     *  TWO distinct Hindi-only tokens. Single-token matches (often
     *  ambiguous loanwords) are not enough. */
    public static boolean looksHindi(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        // Devanagari range — definitive.
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
