package com.aiassistant.callorchestration.voice;

import java.util.function.Consumer;

public interface TextToSpeechProvider {

    String providerId();

    byte[] synthesize(String text, VoiceProfile voice);

    /**
     * Stream synthesized audio chunks as they arrive from the vendor.
     * {@code onChunk} is called from the calling thread; the implementation
     * blocks until the response stream is fully consumed.
     */
    void synthesizeStream(String text, VoiceProfile voice, Consumer<byte[]> onChunk);
}
