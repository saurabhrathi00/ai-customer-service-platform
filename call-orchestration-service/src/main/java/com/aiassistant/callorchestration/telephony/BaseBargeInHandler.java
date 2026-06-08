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
    }

    @Override
    public void cancelTtsGeneration(CallSession session) {
        long newEpoch = session.getTtsEpoch().incrementAndGet();
        log.debug("[barge-in] TTS epoch bumped to {} callId={}", newEpoch, session.getCallId());
    }

    @Override
    public void notifyAiService(CallSession session, String interruptedByText) {
    }

    @Override
    public boolean isBotSpeaking(CallSession session) {
        return System.currentTimeMillis() < session.getEstimatedPlayoutEndMs();
    }

    @Override
    public BargeInAction onPartial(CallSession session, String partialText,
                                   ServiceConfiguration.BargeIn config) {
        if (!config.isEnabled()) return BargeInAction.NONE;
        if (!isBotSpeaking(session)) return BargeInAction.NONE;
        if (session.getBargeInStage() == CallSession.BargeInStage.PAUSED) return BargeInAction.PAUSE;

        String trimmed = partialText == null ? "" : partialText.trim();
        if (trimmed.length() < config.getPartialMinTextLength()) return BargeInAction.NONE;

        String[] words = trimmed.split("\\s+");
        if (words.length < config.getPartialMinWordCount()) return BargeInAction.NONE;

        if (isBackchannel(trimmed)) return BargeInAction.NONE;

        long now = System.currentTimeMillis();

        long botStart = session.getBotSpeakingStartMs();
        if (botStart > 0 && now - botStart < config.getGracePeriodMs()) return BargeInAction.NONE;

        long remainingMs = session.getEstimatedPlayoutEndMs() - now;
        if (remainingMs < config.getRemainingAudioThresholdMs()) return BargeInAction.NONE;

        if (now - session.getLastBargeInMs() < config.getDebounceMs()) return BargeInAction.NONE;

        if (trimmed.length() >= config.getImmediateMinTextLength()
                && words.length >= config.getImmediateMinWordCount()) {
            log.info("[barge-in] IMMEDIATE callId={} partial=\"{}\" remaining={}ms",
                    session.getCallId(), trimmed, remainingMs);
            executeBargeIn(session, trimmed, remainingMs, now);
            return BargeInAction.IMMEDIATE;
        }

        log.info("[barge-in] STAGE1-PAUSE callId={} partial=\"{}\" remaining={}ms",
                session.getCallId(), trimmed, remainingMs);
        session.setBargeInStage(CallSession.BargeInStage.PAUSED);
        session.setBargeInPausedAtMs(now);
        return BargeInAction.PAUSE;
    }

    @Override
    public boolean onFinal(CallSession session, String finalText,
                           ServiceConfiguration.BargeIn config) {
        if (!config.isEnabled()) return false;

        String trimmed = finalText == null ? "" : finalText.trim();
        long now = System.currentTimeMillis();
        boolean wasPaused = session.getBargeInStage() == CallSession.BargeInStage.PAUSED;

        if (wasPaused) {
            long pausedFor = now - session.getBargeInPausedAtMs();
            session.setBargeInStage(CallSession.BargeInStage.NONE);
            session.setBargeInPausedAtMs(0);

            String resumeReason = null;
            if (trimmed.length() < config.getMinTextLength()) {
                resumeReason = "short";
            } else if (isBackchannel(trimmed)) {
                resumeReason = "backchannel";
            } else if (isEchoOfBotSpeech(session, trimmed)) {
                resumeReason = "echo";
            }

            if (resumeReason != null) {
                log.info("[barge-in] STAGE2-RESUME callId={} reason={} text=\"{}\" pausedForMs={}",
                        session.getCallId(), resumeReason, trimmed, pausedFor);
                return false;
            }

            long remainingMs = session.getEstimatedPlayoutEndMs() - now;
            log.info("[barge-in] STAGE2-CONFIRM callId={} text=\"{}\" pausedForMs={}",
                    session.getCallId(), trimmed, pausedFor);
            executeBargeIn(session, trimmed, remainingMs, now);
            return true;
        }

        // No prior pause — final arrived while bot is speaking without partial trigger.
        // Apply full guard checks.
        if (!isBotSpeaking(session)) return false;

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

        if (now - session.getLastBargeInMs() < config.getDebounceMs()) return false;

        long botStart = session.getBotSpeakingStartMs();
        if (botStart > 0 && now - botStart < config.getGracePeriodMs()) return false;

        long remainingMs = session.getEstimatedPlayoutEndMs() - now;
        if (remainingMs < config.getRemainingAudioThresholdMs()) return false;

        executeBargeIn(session, trimmed, remainingMs, now);
        return true;
    }

    private void executeBargeIn(CallSession session, String text, long remainingMs, long now) {
        log.info("[barge-in] EXECUTE callId={} text=\"{}\" remaining={}ms",
                session.getCallId(), text, remainingMs);

        clearCarrierBuffer(session);
        cancelTtsGeneration(session);
        notifyAiService(session, text);

        session.setEstimatedPlayoutEndMs(0);
        session.setBotSpeakingStartMs(0);
        session.setLastBargeInMs(now);
        session.setBargeInStage(CallSession.BargeInStage.NONE);
        session.setBargeInPausedAtMs(0);
    }

    static boolean isBackchannel(String text) {
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
