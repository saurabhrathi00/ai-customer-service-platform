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
        assertNotNull(s.getTtsEpoch(), "ttsEpoch");
        assertNotNull(s.getTtsInFlight(), "ttsInFlight");
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
    void ttsInFlightCounterIsZeroInitially() {
        CallSession s = CallSession.builder().build();
        assertEquals(0, s.getTtsInFlight().get());
    }

    @Test
    void ttsInFlightCounterTransitions() {
        CallSession s = CallSession.builder().build();
        // bot speaking starts
        assertEquals(0, s.getTtsInFlight().getAndIncrement());
        assertEquals(1, s.getTtsInFlight().get());
        s.getTtsInFlight().incrementAndGet();
        assertEquals(2, s.getTtsInFlight().get(), "two chunks in flight");
        s.getTtsInFlight().decrementAndGet();
        s.getTtsInFlight().decrementAndGet();
        assertEquals(0, s.getTtsInFlight().get(), "drained");
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
