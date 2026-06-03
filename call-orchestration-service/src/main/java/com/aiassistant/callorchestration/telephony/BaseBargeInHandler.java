package com.aiassistant.callorchestration.telephony;

import com.aiassistant.callorchestration.configuration.ServiceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class BaseBargeInHandler implements BargeInHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseBargeInHandler.class);

    private static final Set<String> BACKCHANNEL_WORDS = Set.of(
            "hmm", "hm", "mm", "uh", "um", "ok", "okay",
            "haan", "ha", "haan ji", "achha", "accha", "theek",
            "yes", "no", "nahi", "ji");

    @Override
    public void clearCarrierBuffer(CallSession session) {
        // No-op — base provider has no carrier buffer flush mechanism.
        // Audio already sent to carrier will play out naturally.
    }

    @Override
    public void cancelTtsGeneration(CallSession session) {
        long newEpoch = session.getTtsEpoch().incrementAndGet();
        log.debug("[barge-in] TTS epoch bumped to {} callId={}", newEpoch, session.getCallId());
    }

    @Override
    public void notifyAiService(CallSession session, String interruptedByText) {
        // No-op by default. The next MESSAGE sent to ai-conv implicitly
        // supersedes the interrupted turn. Providers can override to send
        // an explicit BARGE_IN event if the AI model benefits from knowing
        // the previous response was cut short.
    }

    @Override
    public boolean isBotSpeaking(CallSession session) {
        return System.currentTimeMillis() < session.getEstimatedPlayoutEndMs();
    }

    @Override
    public boolean tryBargeIn(CallSession session, String sttText,
                              ServiceConfiguration.BargeIn config) {
        if (!config.isEnabled()) return false;
        if (!isBotSpeaking(session)) return false;

        String trimmed = sttText == null ? "" : sttText.trim();
        if (trimmed.length() < config.getMinTextLength()) return false;

        if (isBackchannel(trimmed)) {
            log.debug("[barge-in] SKIP-BACKCHANNEL callId={} text=\"{}\"",
                    session.getCallId(), trimmed);
            return false;
        }

        if (isEchoOfBotSpeech(session, trimmed)) {
            log.info("[barge-in] SKIP-ECHO callId={} text=\"{}\"",
                    session.getCallId(), trimmed);
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - session.getLastBargeInMs() < config.getDebounceMs()) {
            log.debug("[barge-in] SKIP-DEBOUNCE callId={}", session.getCallId());
            return false;
        }

        long botStart = session.getBotSpeakingStartMs();
        if (botStart > 0 && now - botStart < config.getGracePeriodMs()) {
            log.debug("[barge-in] SKIP-GRACE callId={} botSpeakingFor={}ms",
                    session.getCallId(), now - botStart);
            return false;
        }

        long remainingMs = session.getEstimatedPlayoutEndMs() - now;
        if (remainingMs < config.getRemainingAudioThresholdMs()) {
            log.debug("[barge-in] SKIP-ALMOST-DONE callId={} remaining={}ms",
                    session.getCallId(), remainingMs);
            return false;
        }

        // --- Execute barge-in ---
        log.info("[barge-in] TRIGGERED callId={} text=\"{}\" remaining={}ms",
                session.getCallId(), trimmed, remainingMs);

        clearCarrierBuffer(session);
        cancelTtsGeneration(session);
        notifyAiService(session, trimmed);

        session.setEstimatedPlayoutEndMs(0);
        session.setBotSpeakingStartMs(0);
        session.setLastBargeInMs(now);

        return true;
    }

    private static boolean isBackchannel(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (BACKCHANNEL_WORDS.contains(lower)) return true;
        String[] words = lower.split("\\s+");
        return words.length <= 2
                && java.util.Arrays.stream(words).allMatch(BACKCHANNEL_WORDS::contains);
    }

    private static final double ECHO_SIMILARITY_THRESHOLD = 0.5;

    static boolean isEchoOfBotSpeech(CallSession session, String sttText) {
        if (session.getRecentBotUtterances().isEmpty()) return false;
        Set<String> sttWords = toWordSet(sttText);
        if (sttWords.size() < 2) return false;
        long cutoff = System.currentTimeMillis() - 15_000;
        for (CallSession.BotUtterance utterance : session.getRecentBotUtterances()) {
            if (utterance.getTimestampMs() < cutoff) continue;
            Set<String> botWords = toWordSet(utterance.getText());
            if (botWords.isEmpty()) continue;
            long overlap = sttWords.stream().filter(botWords::contains).count();
            double similarity = (double) overlap / Math.min(sttWords.size(), botWords.size());
            if (similarity >= ECHO_SIMILARITY_THRESHOLD) return true;
        }
        return false;
    }

    private static Set<String> toWordSet(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}\\s]", "")
                        .split("\\s+"))
                .filter(w -> !w.isEmpty() && w.length() > 1)
                .collect(Collectors.toSet());
    }
}
