package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

class BargeInHandlerTest {

    private static final int    MIN_CHARS  = 14;
    private static final double MIN_CONF   = 0.5;
    private static final long   MIN_BOT_MS = 400L;

    private AiConversationWsClient aiWs;
    private BargeInHandler handler;
    private CallSession session;

    @BeforeEach
    void setUp() {
        aiWs = Mockito.mock(AiConversationWsClient.class);
        handler = new BargeInHandler(MIN_CHARS, MIN_CONF, MIN_BOT_MS, aiWs);
        session = CallSession.builder()
                .callId("CA-test")
                .conversationId("conv-1")
                .build();
    }

    @Test
    void gate1_botNotSpeaking_doesNotFire() {
        // ttsInFlight = 0, lastTtsActivityMs = 0 — bot is silent.
        TranscriptEvent ev = new TranscriptEvent("This is a long real interruption", false, 0.9);
        assertFalse(handler.checkAndBarge(session, ev),
                "must not barge when bot is silent — that's just the next turn");
    }

    @Test
    void gate1_5_gracePeriod_suppressesEarlyEcho() {
        // Bot speaking, but only just started — echo guard window.
        markBotSpeaking(session, System.currentTimeMillis());
        TranscriptEvent ev = new TranscriptEvent("This is bot echo coming back", false, 0.95);
        assertFalse(handler.checkAndBarge(session, ev),
                "first 400ms of bot speech is immune to barge — suppresses echo");
    }

    @Test
    void gate2_shortInterim_doesNotFire_evenIfBotSpeaking() {
        // Bot has been speaking 2 seconds, real reply (past grace).
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev = new TranscriptEvent("yes", false, 0.95);
        assertFalse(handler.checkAndBarge(session, ev),
                "short interim 'yes' must not stop the bot mid-reply");
    }

    @Test
    void gate2_shortFinal_doesFire() {
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev = new TranscriptEvent("yes", /*isFinal*/ true, 0.95);
        assertTrue(handler.checkAndBarge(session, ev),
                "final transcripts bypass the length gate");
    }

    @Test
    void gate3_lowConfidence_doesNotFire() {
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev = new TranscriptEvent("This is a long interruption phrase", false, 0.3);
        assertFalse(handler.checkAndBarge(session, ev),
                "noisy / low-confidence STT must not fire a barge");
    }

    @Test
    void happyPath_realInterruption_firesAllThreeActions() {
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        // Provider attrs needed by clearTwilioBuffer — but it's a no-op when ws is null.
        TranscriptEvent ev = new TranscriptEvent("Wait, I have a question about the timings", false, 0.9);
        assertTrue(handler.checkAndBarge(session, ev), "valid mid-reply interruption must barge");

        // Action 2: AI WS BARGE_IN sent.
        verify(aiWs).sendBargeIn("conv-1");
        // Action 3: TTS epoch bumped.
        assertTrue(session.getTtsEpoch().get() >= 1, "ttsEpoch must increment");
    }

    @Test
    void idempotency_secondBargeInSameUtteranceIsSuppressed() {
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev1 = new TranscriptEvent("First interim of a long sentence", false, 0.9);
        TranscriptEvent ev2 = new TranscriptEvent("Second interim of the same sentence", false, 0.9);
        assertTrue(handler.checkAndBarge(session, ev1), "first must fire");
        assertFalse(handler.checkAndBarge(session, ev2),
                "subsequent interims within the same utterance must not double-fire");
    }

    @Test
    void reset_rearmsForNextUtterance() {
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev = new TranscriptEvent("First long interruption phrase", false, 0.9);
        assertTrue(handler.checkAndBarge(session, ev));
        handler.reset();
        // After reset, treat as bot speaking again (would otherwise need a fresh
        // ttsInFlight bump).
        markBotSpeaking(session, System.currentTimeMillis() - 2000);
        TranscriptEvent ev2 = new TranscriptEvent("Second long interruption phrase", false, 0.9);
        assertTrue(handler.checkAndBarge(session, ev2),
                "after reset, next utterance can barge again");
    }

    @Test
    void isBotSpeaking_carrierTailKeepsTrueAfterChunksStop() {
        // Simulate a TTS chunk landed 500ms ago — carrier tail keeps speaking=true.
        session.setLastTtsActivityMs(System.currentTimeMillis() - 500);
        assertTrue(BargeInHandler.isBotSpeaking(session));

        // 5 seconds later — well past carrier tail.
        session.setLastTtsActivityMs(System.currentTimeMillis() - 5000);
        assertFalse(BargeInHandler.isBotSpeaking(session));
    }

    /** Set up the session so BargeInHandler thinks the bot has been speaking
     *  since {@code startMs}. ttsInFlight must be > 0 so isBotSpeaking returns
     *  true even if lastTtsActivityMs hasn't been bumped. */
    private static void markBotSpeaking(CallSession s, long startMs) {
        s.getTtsInFlight().set(1);
        s.setBotSpeakingStartMs(startMs);
        s.setLastTtsActivityMs(System.currentTimeMillis());
    }
}
