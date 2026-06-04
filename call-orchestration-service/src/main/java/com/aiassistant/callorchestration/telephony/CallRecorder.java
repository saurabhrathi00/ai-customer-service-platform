package com.aiassistant.callorchestration.telephony;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Temporary per-call audio recorder. Buffers inbound audio in memory
 * and writes a WAV file on flush. Any TelephonyMediaStreamHandler can
 * use this via three static calls:
 * <pre>
 *   CallRecorder.attach(session, codec, sampleRate);   // on start
 *   CallRecorder.push(session, audioBytes);             // each frame
 *   CallRecorder.flush(session);                        // on disconnect
 * </pre>
 *
 * Files are saved as: recordings/{provider}/{phone}_{businessId}_{datetime}.wav
 */
public class CallRecorder {

    private static final Logger log = LoggerFactory.getLogger(CallRecorder.class);
    private static final Path RECORDING_DIR = Path.of("/app/logs/recordings");
    private static final String ATTR_KEY = "recorder";
    private static final DateTimeFormatter FILE_DT_FMT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy_hh-mm-ss-SSSa");

    private final String provider;
    private final String customerPhone;
    private final String businessId;
    private final AudioCodec codec;
    private final int sampleRate;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public CallRecorder(String provider, String customerPhone, String businessId,
                        AudioCodec codec, int sampleRate) {
        this.provider = provider;
        this.customerPhone = customerPhone;
        this.businessId = businessId;
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
            String phone = sanitize(customerPhone != null ? customerPhone.replaceAll("[^0-9]", "") : "unknown");
            String bizId = sanitize(businessId != null ? businessId : "unknown");
            String ts = LocalDateTime.now().format(FILE_DT_FMT);
            String baseName = phone + "_" + bizId + "_" + ts;

            Path dir = RECORDING_DIR.resolve(provider != null ? provider : "unknown");
            Files.createDirectories(dir);

            // Save decoded WAV
            byte[] pcm = (codec == AudioCodec.MULAW) ? mulawToPcm16(raw) : raw;
            Path wavFile = dir.resolve(baseName + ".wav");
            writeWav(wavFile, pcm, sampleRate);

            // Also save raw bytes for debugging codec issues
            Path rawFile = dir.resolve(baseName + ".raw");
            Files.write(rawFile, raw);

            double durationSecs = (double) pcm.length / (sampleRate * 2);
            log.info("[recording] saved {} ({}s, codec={}, rawBytes={})",
                    wavFile, String.format("%.1f", durationSecs), codec, raw.length);
        } catch (Exception ex) {
            log.error("[recording] failed: {}", ex.getMessage());
        }
    }

    // ─── Static helpers for handler convenience ─────────────────────

    public static void attach(CallSession session, AudioCodec codec, int sampleRate) {
        session.getProviderAttributes().put(ATTR_KEY,
                new CallRecorder(
                        session.getProvider(),
                        session.getCustomerPhone(),
                        session.getBusinessId(),
                        codec, sampleRate));
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

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9_-]", "");
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
