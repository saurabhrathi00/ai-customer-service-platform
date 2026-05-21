package com.aiassistant.callorchestration.voice;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class VoiceProfile {
    String voiceId;
    String language;
    Double speakingRate;
}
