package com.aiassistant.callorchestration.voice.elevenlabs;

import com.aiassistant.callorchestration.voice.TextToSpeechProvider;
import com.aiassistant.callorchestration.voice.VoiceProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "configs.tts.provider", havingValue = "elevenlabs", matchIfMissing = true)
public class ElevenLabsTextToSpeechProvider implements TextToSpeechProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTextToSpeechProvider.class);

    @Override
    public String providerId() {
        return "elevenlabs";
    }

    @Override
    public byte[] synthesize(String text, VoiceProfile voice) {
        log.debug("[elevenlabs] synthesize voice={} chars={}",
                voice == null ? null : voice.getVoiceId(), text == null ? 0 : text.length());
        return new byte[0];
    }
}
