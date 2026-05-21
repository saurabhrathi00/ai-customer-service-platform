package com.aiassistant.callorchestration.voice;

public interface TextToSpeechProvider {

    String providerId();

    byte[] synthesize(String text, VoiceProfile voice);
}
