package com.aiassistant.callorchestration.telephony;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Temporary per-call audio recorder. Buffers inbound audio in memory
 * and writes a WAV file on flush. Any TelephonyMediaStreamHandler can
 * use this by storing an instance in CallSession.providerAttributes.
 *
 * <p>Usage:
 * <pre>
 *   // on start
 *   session.getProviderAttributes().put("recorder",
 *       new CallRecorder(session.getCallId(), codec, sampleRate));
 *
 *   // on each audio frame
 *   CallRecorder.push(session, audioBytes);
 *
 *   // on disconnect
 *   CallRecorder.flush(session);
 * </pre>
 */
public class CallRecorder {

    private static final Logger log = LoggerFactory.getLogger(CallRecorder.class);
    private static final Path RECORDING_DIR = Path.of("/app/logs/recordings");
    private static final String ATTR_KEY = "recorder";

    private final String callId;
    private final AudioCodec codec;
    private final int sampleRate;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public CallRecorder(String callId, AudioCodec codec, int sampleRate) {
        this.callId = callId;
        this.codec = codec;
        this.sampleRate = sampleRate;
    }

    public void pushAudio(byte[] audio) {
        synchronized (buffer) {
            buffer.write(audio, 0, audio.length);
        }
    }

    public void save() {
        byte[] raw;
        synchronized (buffer) {
            raw = buffer.toByteArray();
            buffer.reset();
        }
        if (raw.length == 0) return;

        try {
            byte[] pcm = (codec == AudioCodec.MULAW) ? mulawToPcm16(raw) : raw;
            Files.createDirectories(RECORDING_DIR);
            Path wavFile = RECORDING_DIR.resolve(callId + ".wav");
            writeWav(wavFile, pcm, sampleRate);
            double durationSecs = (double) pcm.length / (sampleRate * 2);
            log.info("[recording] saved {} ({} bytes, {}s) callId={}",
                    wavFile, pcm.length, String.format("%.1f", durationSecs), callId);
        } catch (Exception ex) {
            log.error("[recording] failed to save callId={}: {}", callId, ex.getMessage());
        }
    }

    // ─── Static helpers for handler convenience ─────────────────────

    public static void attach(CallSession session, AudioCodec codec, int sampleRate) {
        session.getProviderAttributes().put(ATTR_KEY,
                new CallRecorder(session.getCallId(), codec, sampleRate));
    }

    public static void push(CallSession session, byte[] audio) {
        Object r = session.getProviderAttributes().get(ATTR_KEY);
        if (r instanceof CallRecorder rec) rec.pushAudio(audio);
    }

    public static void flush(CallSession session) {
        Object r = session.getProviderAttributes().remove(ATTR_KEY);
        if (r instanceof CallRecorder rec) rec.save();
    }

    // ─── WAV writing ────────────────────────────────────────────────

    private static void writeWav(Path path, byte[] pcmData, int sampleRate) throws IOException {
        int channels = 1;
        int bitsPerSample = 16;
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.writeBytes("RIFF");
            raf.write(intToLE(chunkSize));
            raf.writeBytes("WAVE");
            raf.writeBytes("fmt ");
            raf.write(intToLE(16));
            raf.write(shortToLE((short) 1));
            raf.write(shortToLE((short) channels));
            raf.write(intToLE(sampleRate));
            raf.write(intToLE(byteRate));
            raf.write(shortToLE((short) blockAlign));
            raf.write(shortToLE((short) bitsPerSample));
            raf.writeBytes("data");
            raf.write(intToLE(dataSize));
            raf.write(pcmData);
        }
    }

    private static byte[] intToLE(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] shortToLE(short v) {
        return new byte[]{(byte) v, (byte) (v >> 8)};
    }

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

    private static byte[] mulawToPcm16(byte[] mulaw) {
        byte[] pcm = new byte[mulaw.length * 2];
        for (int i = 0; i < mulaw.length; i++) {
            short sample = MULAW_DECODE[mulaw[i] & 0xFF];
            pcm[i * 2] = (byte) (sample & 0xFF);
            pcm[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return pcm;
    }
}
