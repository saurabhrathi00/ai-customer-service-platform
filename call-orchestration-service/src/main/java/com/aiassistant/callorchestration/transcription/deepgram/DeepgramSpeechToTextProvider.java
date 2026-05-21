package com.aiassistant.callorchestration.transcription.deepgram;

import com.aiassistant.callorchestration.telephony.AudioCodec;
import com.aiassistant.callorchestration.transcription.SpeechToTextProvider;
import com.aiassistant.callorchestration.transcription.SttSession;
import com.aiassistant.callorchestration.transcription.TranscriptEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@ConditionalOnProperty(name = "configs.stt.provider", havingValue = "deepgram")
public class DeepgramSpeechToTextProvider implements SpeechToTextProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepgramSpeechToTextProvider.class);

    @Override
    public String providerId() {
        return "deepgram";
    }

    @Override
    public SttSession openSession(String callId, AudioCodec codec, int sampleRateHz,
                                  Consumer<TranscriptEvent> onTranscript) {
        log.warn("[deepgram] STUB session opened callId={} codec={} rate={}", callId, codec, sampleRateHz);
        return new SttSession() {
            @Override public void pushAudio(byte[] audio) { /* no-op */ }
            @Override public void close() { log.warn("[deepgram] STUB session closed callId={}", callId); }
        };
    }
}