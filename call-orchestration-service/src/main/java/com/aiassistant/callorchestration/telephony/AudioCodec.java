package com.aiassistant.callorchestration.telephony;

/**
 * Audio codec used on the telephony media stream. Carries the bytes-per-ms
 * value for each codec at its canonical telephony sample rate so playback
 * duration can be derived from raw byte counts (e.g. for end-of-turn timing
 * after a TTS reply).
 */
public enum AudioCodec {
    /** 16-bit linear PCM at 16 kHz → 2 bytes/sample × 16 samples/ms = 32 bytes/ms. */
    PCM16(32),
    /** μ-law (G.711) at 8 kHz → 1 byte/sample × 8 samples/ms = 8 bytes/ms. */
    MULAW(8);

    private final int bytesPerMs;

    AudioCodec(int bytesPerMs) {
        this.bytesPerMs = bytesPerMs;
    }

    /** Bytes of encoded audio per millisecond of playback at this codec's
     *  canonical telephony sample rate. Use for deriving playback duration
     *  from a buffered byte count. */
    public int bytesPerMs() {
        return bytesPerMs;
    }
}
