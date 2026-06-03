package com.aiassistant.callorchestration.telephony;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallSessionTest {

    @Test
    void defaultsAreNonNull() {
        CallSession s = CallSession.builder().build();
        assertNotNull(s.getTranscript(), "transcript list");
        assertNotNull(s.getHistory(), "history list");
        assertNotNull(s.getProviderAttributes(), "providerAttributes map");
        assertNotNull(s.getFinalized(), "finalized");
        assertNotNull(s.getEnded(), "ended");
        assertNotNull(s.getEndingCall(), "endingCall");
        assertNotNull(s.getGreetingDone(), "greetingDone");
        assertNotNull(s.getPendingUtterance(), "pendingUtterance");
    }

    @Test
    void endingCallLatchIsIdempotent() {
        CallSession s = CallSession.builder().build();
        assertTrue(s.getEndingCall().compareAndSet(false, true),
                "first latch should win");
        assertFalse(s.getEndingCall().compareAndSet(false, true),
                "second latch must be rejected — that's how duplicate HANGUP gets dropped");
    }

    @Test
    void finalizedLatchIsIdempotent() {
        CallSession s = CallSession.builder().build();
        assertTrue(s.getFinalized().compareAndSet(false, true));
        assertFalse(s.getFinalized().compareAndSet(false, true),
                "second finalize must be a no-op");
    }

    @Test
    void greetingDoneStartsFalse() {
        CallSession s = CallSession.builder().build();
        assertFalse(s.getGreetingDone().get(),
                "STT must be gated off until greeting completes");
    }

    @Test
    void pendingUtteranceIsMutable() {
        CallSession s = CallSession.builder().build();
        s.getPendingUtterance().append("hello");
        assertEquals("hello", s.getPendingUtterance().toString());
        s.getPendingUtterance().setLength(0);
        assertEquals(0, s.getPendingUtterance().length());
    }

    @Test
    void ttsEpochStartsAtZero() {
        CallSession s = CallSession.builder().build();
        assertNotNull(s.getTtsEpoch(), "ttsEpoch");
        assertEquals(0L, s.getTtsEpoch().get());
    }

    @Test
    void ttsEpochIncrements() {
        CallSession s = CallSession.builder().build();
        assertEquals(1L, s.getTtsEpoch().incrementAndGet());
        assertEquals(2L, s.getTtsEpoch().incrementAndGet());
    }

    @Test
    void playoutEstimateDefaultsToZero() {
        CallSession s = CallSession.builder().build();
        assertEquals(0L, s.getEstimatedPlayoutEndMs(),
                "no audio sent → bot not speaking");
        assertFalse(System.currentTimeMillis() < s.getEstimatedPlayoutEndMs(),
                "isBotSpeaking should be false with default");
    }

    @Test
    void bargeInFieldsDefaultToZero() {
        CallSession s = CallSession.builder().build();
        assertEquals(0L, s.getBotSpeakingStartMs());
        assertEquals(0L, s.getLastBargeInMs());
    }

    @Test
    void recentBotUtterancesDefaultEmpty() {
        CallSession s = CallSession.builder().build();
        assertNotNull(s.getRecentBotUtterances());
        assertTrue(s.getRecentBotUtterances().isEmpty());
    }

    @Test
    void recordBotUtteranceAddsEntry() {
        CallSession s = CallSession.builder().build();
        s.recordBotUtterance("hello world");
        assertEquals(1, s.getRecentBotUtterances().size());
        assertEquals("hello world", s.getRecentBotUtterances().peekFirst().getText());
    }

    @Test
    void recordBotUtteranceIgnoresBlank() {
        CallSession s = CallSession.builder().build();
        s.recordBotUtterance("");
        s.recordBotUtterance(null);
        s.recordBotUtterance("   ");
        assertTrue(s.getRecentBotUtterances().isEmpty());
    }
}
