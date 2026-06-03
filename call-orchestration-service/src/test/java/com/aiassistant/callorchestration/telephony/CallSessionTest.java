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
}
