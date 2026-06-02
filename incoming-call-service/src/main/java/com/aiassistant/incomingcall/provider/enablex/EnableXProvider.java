package com.aiassistant.incomingcall.provider.enablex;

import com.aiassistant.incomingcall.configuration.SecretsConfiguration;
import com.aiassistant.incomingcall.provider.IncomingCallRequest;
import com.aiassistant.incomingcall.provider.StreamHandoff;
import com.aiassistant.incomingcall.provider.TelephonyProvider;
import com.aiassistant.incomingcall.provider.TelephonyResponse;
import com.aiassistant.incomingcall.provider.TelephonySignatureInvalidException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        prefix = "secrets.enablex", name = "appId")
public class EnableXProvider implements TelephonyProvider {

    private static final Logger log = LoggerFactory.getLogger(EnableXProvider.class);
    private static final String DECRYPTED_ATTR = "enablex.decrypted";

    private final SecretsConfiguration secretsConfiguration;
    private final EnableXApiClient enableXApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "enablex";
    }

    @Override
    public void verifySignature(HttpServletRequest request) {
        try {
            StringBuilder hdrs = new StringBuilder();
            request.getHeaderNames().asIterator().forEachRemaining(h ->
                    hdrs.append(h).append("=").append(request.getHeader(h)).append("; "));
            log.info("[enablex] headers: {}", hdrs);

            byte[] body = request.getInputStream().readAllBytes();
            if (body.length == 0) {
                log.info("Empty body — treating as EnableX URL validation probe");
                return;
            }

            String rawBody = new String(body, StandardCharsets.UTF_8);
            log.info("[enablex] raw body ({} bytes): {}", body.length, rawBody);

            JsonNode root = objectMapper.readTree(body);
            String encryptedData = root.path("encrypted_data").asText(null);
            if (encryptedData == null || encryptedData.isBlank()) {
                log.info("No encrypted_data — fields present: {}", root.fieldNames().hasNext() ? root : "none");
                return;
            }

            String algo = request.getHeader("x-algoritm");
            String format = request.getHeader("x-format");
            String encoding = request.getHeader("x-encoding");

            String decrypted = decrypt(encryptedData, algo, format, encoding);
            request.setAttribute(DECRYPTED_ATTR, decrypted);

        } catch (TelephonySignatureInvalidException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TelephonySignatureInvalidException("EnableX decryption failed: " + ex.getMessage());
        }
    }

    @Override
    public IncomingCallRequest parseRequest(HttpServletRequest request) {
        String decrypted = (String) request.getAttribute(DECRYPTED_ATTR);
        if (decrypted == null) {
            return IncomingCallRequest.builder().build();
        }
        try {
            JsonNode event = objectMapper.readTree(decrypted);
            return IncomingCallRequest.builder()
                    .callId(event.path("voice_id").asText(null))
                    .fromNumber(event.path("from").asText(null))
                    .toNumber(event.path("to").asText(null))
                    .callStatus(event.path("state").asText(null))
                    .build();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to parse decrypted EnableX event", ex);
        }
    }

    @Override
    public TelephonyResponse buildStreamHandoff(StreamHandoff handoff) {
        return new TelephonyResponse("{}", MediaType.APPLICATION_JSON);
    }

    @Override
    public TelephonyResponse buildUnknownNumberResponse() {
        return new TelephonyResponse("{}", MediaType.APPLICATION_JSON);
    }

    @Override
    public void afterHandoff(StreamHandoff handoff) {
        CompletableFuture.runAsync(() -> {
            try {
                enableXApiClient.acceptCall(handoff.getCallId());
                enableXApiClient.startStream(handoff.getCallId(), handoff.getWsUrl());
            } catch (Exception ex) {
                log.error("EnableX post-handoff failed for voiceId={}: {}",
                        handoff.getCallId(), ex.getMessage(), ex);
            }
        });
    }

    private String decrypt(String encryptedData, String algo, String format, String encoding) throws Exception {
        String appId = secretsConfiguration.getEnablex().getAppId();

        byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(
                appId.getBytes(StandardCharsets.UTF_8));
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        byte[] encBytes;
        if ("hex".equalsIgnoreCase(format)) {
            encBytes = HexFormat.of().parseHex(encryptedData);
        } else {
            encBytes = java.util.Base64.getDecoder().decode(encryptedData);
        }

        // IV is the first 16 bytes, ciphertext is the rest
        byte[] iv = new byte[16];
        byte[] ciphertext = new byte[encBytes.length - 16];
        System.arraycopy(encBytes, 0, iv, 0, 16);
        System.arraycopy(encBytes, 16, ciphertext, 0, ciphertext.length);

        String transformation = "AES/CBC/PKCS5Padding";
        if (algo != null && !algo.isBlank()) {
            log.debug("EnableX x-algoritm header: {}", algo);
        }

        Cipher cipher = Cipher.getInstance(transformation);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] decrypted = cipher.doFinal(ciphertext);

        String enc = (encoding != null && !encoding.isBlank()) ? encoding : "UTF-8";
        return new String(decrypted, enc);
    }
}
