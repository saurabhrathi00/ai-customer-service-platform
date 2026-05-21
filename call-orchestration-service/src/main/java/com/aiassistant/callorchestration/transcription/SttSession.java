package com.aiassistant.callorchestration.transcription;

/**
 * Per-call streaming STT session. Push audio frames as they arrive from the
 * telephony provider; transcripts are delivered via the callback registered
 * at {@link SpeechToTextProvider#openSession}.
 */
public interface SttSession extends AutoCloseable {

    void pushAudio(byte[] audio);

    @Override
    void close();
}