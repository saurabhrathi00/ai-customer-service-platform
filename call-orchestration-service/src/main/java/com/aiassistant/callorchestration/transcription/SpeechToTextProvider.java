package com.aiassistant.callorchestration.transcription;

import com.aiassistant.callorchestration.telephony.AudioCodec;

import java.util.function.Consumer;

public interface SpeechToTextProvider {

    String providerId();

    /**
     * Open a streaming transcription session for a single call. The provider
     * holds the WebSocket / connection internally; the caller pushes audio
     * frames in real time and receives transcripts via the callback.
     */
    SttSession openSession(String callId,
                           AudioCodec codec,
                           int sampleRateHz,
                           Consumer<TranscriptEvent> onTranscript);
}