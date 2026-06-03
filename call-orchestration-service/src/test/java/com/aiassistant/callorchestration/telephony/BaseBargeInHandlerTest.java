package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseBargeInHandlerTest {

    private BaseBargeInHandler handler;
    private ServiceConfiguration.BargeIn config;
    private CallSession session;

    @BeforeEach
    void setUp() {
        handler = new BaseBargeInHandler();
        config = new ServiceConfiguration.BargeIn();
        session = CallSession.builder().callId("test-call").build();
    }

    private void makeBotSpeaking(long remainingMs) {
        long now = System.currentTimeMillis();
        session.setEstimatedPlayoutEndMs(now + remainingMs);
        session.setBotSpeakingStartMs(now - 2000);
    }

    @Test
    void noBarge_whenBotNotSpeaking() {
        assertFalse(handler.tryBargeIn(session, "hello there", config));
    }

    @Test
    void noBarge_whenDisabled() {
        config.setEnabled(false);
        makeBotSpeaking(5000);
        assertFalse(handler.tryBargeIn(session, "hello there", config));
    }

    @Test
    void noBarge_whenTextTooShort() {
        makeBotSpeaking(5000);
        assertFalse(handler.tryBargeIn(session, "hi", config));
    }

    @Test
    void noBarge_onBackchannel() {
        makeBotSpeaking(5000);
        assertFalse(handler.tryBargeIn(session, "hmm", config));
        assertFalse(handler.tryBargeIn(session, "haan ji", config));
        assertFalse(handler.tryBargeIn(session, "ok", config));
        assertFalse(handler.tryBargeIn(session, "achha", config));
    }

    @Test
    void noBarge_duringGracePeriod() {
        makeBotSpeaking(5000);
        session.setBotSpeakingStartMs(System.currentTimeMillis() - 100);
        assertFalse(handler.tryBargeIn(session, "what is the price", config));
    }

    @Test
    void noBarge_whenAlmostDoneSpeaking() {
        makeBotSpeaking(200);
        assertFalse(handler.tryBargeIn(session, "what is the price", config));
    }

    @Test
    void noBarge_whenDebounceActive() {
        makeBotSpeaking(5000);
        session.setLastBargeInMs(System.currentTimeMillis() - 100);
        assertFalse(handler.tryBargeIn(session, "what is the price", config));
    }

    @Test
    void barges_whenAllConditionsMet() {
        makeBotSpeaking(5000);
        assertTrue(handler.tryBargeIn(session, "what is the price", config));
        assertEquals(0L, session.getEstimatedPlayoutEndMs(),
                "playout should be reset after barge-in");
        assertEquals(0L, session.getBotSpeakingStartMs(),
                "botSpeakingStart should be reset after barge-in");
        assertTrue(session.getLastBargeInMs() > 0,
                "lastBargeInMs should be set");
        assertEquals(1L, session.getTtsEpoch().get(),
                "TTS epoch should be bumped");
    }

    @Test
    void debounce_preventsRapidBargeIns() {
        makeBotSpeaking(10000);
        assertTrue(handler.tryBargeIn(session, "stop stop stop", config));
        makeBotSpeaking(10000);
        assertFalse(handler.tryBargeIn(session, "I said stop", config),
                "second barge-in within debounce window should be rejected");
    }

    @Test
    void multiWordBackchannel_isFiltered() {
        makeBotSpeaking(5000);
        assertFalse(handler.tryBargeIn(session, "ok ok", config));
        assertFalse(handler.tryBargeIn(session, "haan theek", config));
    }

    @Test
    void realSpeech_isNotBackchannel() {
        makeBotSpeaking(5000);
        assertTrue(handler.tryBargeIn(session, "mujhe price batao", config));
    }
}
