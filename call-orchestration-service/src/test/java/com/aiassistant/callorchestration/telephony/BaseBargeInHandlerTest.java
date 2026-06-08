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
        assertFalse(handler.onFinal(session, "hello there", config));
    }

    @Test
    void noBarge_whenDisabled() {
        config.setEnabled(false);
        makeBotSpeaking(5000);
        assertFalse(handler.onFinal(session, "hello there", config));
    }

    @Test
    void noBarge_whenTextTooShort() {
        makeBotSpeaking(5000);
        assertFalse(handler.onFinal(session, "hi", config));
    }

    @Test
    void noBarge_onBackchannel() {
        makeBotSpeaking(5000);
        assertFalse(handler.onFinal(session, "hmm", config));
        assertFalse(handler.onFinal(session, "haan ji", config));
        assertFalse(handler.onFinal(session, "ok", config));
        assertFalse(handler.onFinal(session, "achha", config));
    }

    @Test
    void noBarge_duringGracePeriod() {
        makeBotSpeaking(5000);
        session.setBotSpeakingStartMs(System.currentTimeMillis() - 100);
        assertFalse(handler.onFinal(session, "what is the price", config));
    }

    @Test
    void noBarge_whenAlmostDoneSpeaking() {
        makeBotSpeaking(200);
        assertFalse(handler.onFinal(session, "what is the price", config));
    }

    @Test
    void noBarge_whenDebounceActive() {
        makeBotSpeaking(5000);
        session.setLastBargeInMs(System.currentTimeMillis() - 100);
        assertFalse(handler.onFinal(session, "what is the price", config));
    }

    @Test
    void barges_whenAllConditionsMet() {
        makeBotSpeaking(5000);
        assertTrue(handler.onFinal(session, "what is the price", config));
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
        assertTrue(handler.onFinal(session, "stop stop stop", config));
        makeBotSpeaking(10000);
        assertFalse(handler.onFinal(session, "I said stop", config),
                "second barge-in within debounce window should be rejected");
    }

    @Test
    void multiWordBackchannel_isFiltered() {
        makeBotSpeaking(5000);
        assertFalse(handler.onFinal(session, "ok ok", config));
        assertFalse(handler.onFinal(session, "haan theek", config));
    }

    @Test
    void realSpeech_isNotBackchannel() {
        makeBotSpeaking(5000);
        assertTrue(handler.onFinal(session, "mujhe price batao", config));
    }

    @Test
    void noBarge_whenEchoOfBotSpeech() {
        makeBotSpeaking(5000);
        session.recordBotUtterance("अच्छा, तो building systems के बारे में बताइए।");
        assertFalse(handler.onFinal(session, "building systems के बारे में बताइए", config));
    }

    @Test
    void barges_whenTextDifferentFromBot() {
        makeBotSpeaking(5000);
        session.recordBotUtterance("हमारे पास roofing solutions हैं");
        assertTrue(handler.onFinal(session, "mujhe price batao please", config));
    }

    @Test
    void echoDetection_ignoresOldUtterances() {
        makeBotSpeaking(5000);
        CallSession.BotUtterance old = CallSession.BotUtterance.builder()
                .text("building systems ke baare mein").timestampMs(System.currentTimeMillis() - 20_000).build();
        session.getRecentBotUtterances().addLast(old);
        assertTrue(handler.onFinal(session, "building systems ke baare mein batao", config));
    }

    @Test
    void echoDetection_wordOverlapWorks() {
        assertTrue(BaseBargeInHandler.isEchoOfBotSpeech(
                sessionWithBotText("hello how are you doing today"),
                "how are you doing today"));
        assertFalse(BaseBargeInHandler.isEchoOfBotSpeech(
                sessionWithBotText("hello how are you doing today"),
                "mujhe price batao please"));
    }

    private CallSession sessionWithBotText(String text) {
        CallSession s = CallSession.builder().callId("echo-test").build();
        s.recordBotUtterance(text);
        return s;
    }
}
