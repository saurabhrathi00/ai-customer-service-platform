package com.aiassistant.callorchestration.telephony.audio;

import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

/**
 * Per-session RNNoise denoiser for 8 kHz μ-law audio.
 *
 * Pipeline: mulaw 8k → PCM16 → upsample 48k → RNNoise → downsample 8k → mulaw
 *
 * RNNoise operates on 480-sample frames at 48 kHz (10 ms).
 * At 8 kHz that maps to 80 mulaw bytes per frame.
 * Incoming audio is buffered until a full 80-byte chunk is available.
 */
public class RNNoiseProcessor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RNNoiseProcessor.class);

    private static final int RNNOISE_FRAME = 480;   // samples at 48 kHz
    private static final int INPUT_FRAME = 80;       // mulaw bytes at 8 kHz (10 ms)
    private static final int UPSAMPLE_RATIO = 6;     // 48000 / 8000

    private static final boolean AVAILABLE;
    static {
        boolean ok;
        try {
            RNNoiseLib.INSTANCE.rnnoise_get_frame_size();
            ok = true;
        } catch (UnsatisfiedLinkError e) {
            log.warn("[rnnoise] native library not available — denoising disabled: {}", e.getMessage());
            ok = false;
        }
        AVAILABLE = ok;
    }

    public static boolean isAvailable() { return AVAILABLE; }

    private final Pointer state;
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    private boolean closed;

    public RNNoiseProcessor() {
        state = RNNoiseLib.INSTANCE.rnnoise_create(null);
    }

    /**
     * Feed raw mulaw 8 kHz bytes. Returns denoised mulaw bytes
     * (may be shorter than input if a partial frame is buffered).
     */
    public byte[] process(byte[] mulaw) {
        if (closed || mulaw == null || mulaw.length == 0) return mulaw;

        buf.write(mulaw, 0, mulaw.length);
        byte[] buffered = buf.toByteArray();

        int fullFrames = buffered.length / INPUT_FRAME;
        if (fullFrames == 0) return new byte[0];

        int processBytes = fullFrames * INPUT_FRAME;
        byte[] output = new byte[processBytes];

        for (int f = 0; f < fullFrames; f++) {
            int off = f * INPUT_FRAME;
            processOneFrame(buffered, off, output, off);
        }

        int remainder = buffered.length - processBytes;
        buf.reset();
        if (remainder > 0) {
            buf.write(buffered, processBytes, remainder);
        }

        return output;
    }

    private void processOneFrame(byte[] in, int inOff, byte[] out, int outOff) {
        // 1. mulaw → PCM16 (80 samples)
        short[] pcm8k = new short[INPUT_FRAME];
        for (int i = 0; i < INPUT_FRAME; i++) {
            pcm8k[i] = MULAW_DECODE[in[inOff + i] & 0xFF];
        }

        // 2. upsample 8k → 48k via linear interpolation (480 samples)
        float[] f48k = new float[RNNOISE_FRAME];
        for (int i = 0; i < INPUT_FRAME - 1; i++) {
            float s0 = pcm8k[i];
            float s1 = pcm8k[i + 1];
            for (int j = 0; j < UPSAMPLE_RATIO; j++) {
                f48k[i * UPSAMPLE_RATIO + j] = s0 + (s1 - s0) * j / UPSAMPLE_RATIO;
            }
        }
        // last sample — hold
        float last = pcm8k[INPUT_FRAME - 1];
        for (int j = 0; j < UPSAMPLE_RATIO; j++) {
            f48k[(INPUT_FRAME - 1) * UPSAMPLE_RATIO + j] = last;
        }

        // 3. RNNoise denoise
        float[] cleaned = new float[RNNOISE_FRAME];
        RNNoiseLib.INSTANCE.rnnoise_process_frame(state, cleaned, f48k);

        // 4. downsample 48k → 8k (take every 6th sample)
        short[] pcmOut = new short[INPUT_FRAME];
        for (int i = 0; i < INPUT_FRAME; i++) {
            float v = cleaned[i * UPSAMPLE_RATIO];
            pcmOut[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
        }

        // 5. PCM16 → mulaw
        for (int i = 0; i < INPUT_FRAME; i++) {
            out[outOff + i] = pcm16ToMulaw(pcmOut[i]);
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            RNNoiseLib.INSTANCE.rnnoise_destroy(state);
        }
    }

    // ─── μ-law codec tables ────────────────────────────────────────

    private static final short[] MULAW_DECODE = new short[256];
    static {
        for (int i = 0; i < 256; i++) {
            int mu = ~i & 0xFF;
            int sign = (mu & 0x80) != 0 ? -1 : 1;
            int exponent = (mu >> 4) & 0x07;
            int mantissa = mu & 0x0F;
            int magnitude = ((mantissa << 1) + 33) << (exponent + 2);
            magnitude -= 0x84;
            MULAW_DECODE[i] = (short) (sign * magnitude);
        }
    }

    private static byte pcm16ToMulaw(short sample) {
        int sign = 0;
        int s = sample;
        if (s < 0) { sign = 0x80; s = -s; }
        if (s > 32635) s = 32635;
        s += 0x84;
        int exponent = 7;
        for (int mask = 0x4000; (s & mask) == 0 && exponent > 0; exponent--, mask >>= 1) {}
        int mantissa = (s >> (exponent + 3)) & 0x0F;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }
}
