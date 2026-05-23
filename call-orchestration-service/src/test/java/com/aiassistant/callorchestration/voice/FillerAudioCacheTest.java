package com.aiassistant.callorchestration.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-function tests for {@link FillerAudioCache#looksHindi(String)}.
 *  No Spring context needed — the heuristic is a static method. */
class FillerAudioCacheTest {

    @Test
    void devanagari_isHindi() {
        assertTrue(FillerAudioCache.looksHindi("मेरा नाम राहुल है"));
        assertTrue(FillerAudioCache.looksHindi("Hello, मुझे information chahiye"));
    }

    @Test
    void pureEnglish_isNotHindi() {
        assertFalse(FillerAudioCache.looksHindi("What are your timings"));
        assertFalse(FillerAudioCache.looksHindi("I want to know about the products"));
        assertFalse(FillerAudioCache.looksHindi("Yes please tell me more"));
    }

    @Test
    void singleAmbiguousToken_isNotHindi_byTwoTokenThreshold() {
        // "to" was a known false-positive when it was a stand-alone Hindi
        // token. Now the heuristic requires TWO distinct Hindi tokens.
        assertFalse(FillerAudioCache.looksHindi("I want to know more"),
                "ambiguous English word 'to' must not trigger Hindi");
    }

    @Test
    void twoHindiTokens_isHindi() {
        assertTrue(FillerAudioCache.looksHindi("haan bhai bata do"),
                "haan + bhai + bata — three Hindi tokens, easily wins");
    }

    @Test
    void nullOrBlank_isNotHindi() {
        assertFalse(FillerAudioCache.looksHindi(null));
        assertFalse(FillerAudioCache.looksHindi(""));
        assertFalse(FillerAudioCache.looksHindi("   "));
    }

    @Test
    void hinglish_isHindi() {
        assertTrue(FillerAudioCache.looksHindi("aap kya kar rahe hain"));
        assertTrue(FillerAudioCache.looksHindi("Bhai timings kya hain"));
    }

    @Test
    void questionWordInEnglish_doesNotTriggerHindi() {
        // 'how' is English, not in Hindi token set.
        assertFalse(FillerAudioCache.looksHindi("How can I help you"));
    }
}
