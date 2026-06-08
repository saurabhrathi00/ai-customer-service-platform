package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import com.aiassistant.callorchestration.services.ConversationCoordinator;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import com.aiassistant.callorchestration.voice.FillerAudioCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Common STT event handler shared by all telephony providers.
 * Handles greeting gate, noise filtering, barge-in, filler audio,
 * and forwarding to {@link ConversationCoordinator}.
 */
public class SttCallbackHandler implements Consumer<TranscriptEvent> {

    private static final Logger log = LoggerFactory.getLogger(SttCallbackHandler.class);
    private static final int MIN_FORWARD_CHARS = 2;

    private final CallSession session;
    private final ConversationCoordinator conversationCoordinator;
    private final BargeInHandler bargeInHandler;
    private final ServiceConfiguration serviceConfiguration;
    private final FillerAudioCache fillerAudioCache;
    private final Consumer<TranscriptEvent> extraCallback;

    private SttCallbackHandler(Builder b) {
        this.session = b.session;
        this.conversationCoordinator = b.conversationCoordinator;
        this.bargeInHandler = b.bargeInHandler;
        this.serviceConfiguration = b.serviceConfiguration;
        this.fillerAudioCache = b.fillerAudioCache;
        this.extraCallback = b.extraCallback;
    }

    @Override
    public void accept(TranscriptEvent sttEvent) {
        String text = sttEvent.text();
        if (text == null || text.isBlank()) return;

        if (!session.getGreetingDone().get()) {
            log.debug("[stt] dropped during greeting callId={} text=\"{}\"",
                    session.getCallId(), text);
            return;
        }

        session.setLastCallerActivityMs(System.currentTimeMillis());
        session.setSilenceNudgedAtMs(0L);

        if (extraCallback != null) extraCallback.accept(sttEvent);

        if (!sttEvent.isFinal()) {
            // Stage 1: attempt pause or immediate barge-in on partial
            if (bargeInHandler != null && serviceConfiguration != null) {
                BargeInHandler.BargeInAction action = bargeInHandler.tryPartialBargeIn(
                        session, text.trim(), serviceConfiguration.getBargeIn());
                if (action == BargeInHandler.BargeInAction.IMMEDIATE) {
                    log.info("[stt] immediate barge-in on partial callId={} text=\"{}\"",
                            session.getCallId(), text.trim());
                }
            }
            return;
        }

        // ── Final processing ──
        String trimmed = text.trim();
        if (trimmed.length() < MIN_FORWARD_CHARS) {
            if (session.getBargeInStage() == CallSession.BargeInStage.PAUSED) {
                log.info("[stt] STAGE2-RESUME-NOISE callId={} reason=too-short",
                        session.getCallId());
                session.setBargeInStage(CallSession.BargeInStage.NONE);
                session.setBargeInPausedAtMs(0);
            }
            log.info("[stt] DROP-NOISE callId={} reason=too-short len={} text=\"{}\"",
                    session.getCallId(), trimmed.length(), text);
            return;
        }

        if (bargeInHandler != null && serviceConfiguration != null) {
            boolean barged;
            if (session.getBargeInStage() == CallSession.BargeInStage.PAUSED) {
                barged = bargeInHandler.resolveAfterPause(
                        session, trimmed, serviceConfiguration.getBargeIn());
            } else {
                barged = bargeInHandler.tryBargeIn(
                        session, trimmed, serviceConfiguration.getBargeIn());
            }

            if (barged) {
                log.info("[stt] barge-in accepted callId={} text=\"{}\"",
                        session.getCallId(), trimmed);
            }

            if (!barged && fillerAudioCache != null && fillerAudioCache.isEnabled()) {
                playFiller(text);
            }
        } else if (fillerAudioCache != null && fillerAudioCache.isEnabled()) {
            playFiller(text);
        }

        conversationCoordinator.onCustomerUtterance(
                session.getCallId(), text, true, sttEvent.confidence());
    }

    private void playFiller(String text) {
        Object l = session.getProviderAttributes().get("aiCallListener");
        if (l instanceof FillerCapable fc) fc.maybePlayFiller(text);
    }

    public interface FillerCapable {
        void maybePlayFiller(String text);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private CallSession session;
        private ConversationCoordinator conversationCoordinator;
        private BargeInHandler bargeInHandler;
        private ServiceConfiguration serviceConfiguration;
        private FillerAudioCache fillerAudioCache;
        private Consumer<TranscriptEvent> extraCallback;

        public Builder session(CallSession s) { this.session = s; return this; }
        public Builder conversationCoordinator(ConversationCoordinator c) { this.conversationCoordinator = c; return this; }
        public Builder bargeInHandler(BargeInHandler b) { this.bargeInHandler = b; return this; }
        public Builder serviceConfiguration(ServiceConfiguration s) { this.serviceConfiguration = s; return this; }
        public Builder fillerAudioCache(FillerAudioCache f) { this.fillerAudioCache = f; return this; }
        public Builder extraCallback(Consumer<TranscriptEvent> cb) { this.extraCallback = cb; return this; }
        public SttCallbackHandler build() { return new SttCallbackHandler(this); }
    }
}
