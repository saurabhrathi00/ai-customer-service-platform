package com.aiassistant.incomingcall.provider.twilio;

import com.aiassistant.incomingcall.provider.IncomingCallRequest;
import com.aiassistant.incomingcall.provider.StreamHandoff;
import com.aiassistant.incomingcall.provider.TelephonyProvider;
import com.aiassistant.incomingcall.provider.TelephonyResponse;
import com.aiassistant.incomingcall.provider.TelephonySignatureInvalidException;
import com.twilio.security.RequestValidator;
import com.twilio.twiml.VoiceResponse;
import com.twilio.twiml.voice.Connect;
import com.twilio.twiml.voice.Hangup;
import com.twilio.twiml.voice.Parameter;
import com.twilio.twiml.voice.Say;
import com.twilio.twiml.voice.Stream;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TwilioProvider implements TelephonyProvider {

    private static final String SIGNATURE_HEADER = "X-Twilio-Signature";

    private final RequestValidator requestValidator;

    @Override
    public String name() {
        return "twilio";
    }

    @Override
    public void verifySignature(HttpServletRequest request) {
        String signature = request.getHeader(SIGNATURE_HEADER);
        if (signature == null || signature.isBlank()) {
            throw new TelephonySignatureInvalidException("Missing " + SIGNATURE_HEADER + " header");
        }

        StringBuilder url = new StringBuilder(request.getRequestURL().toString());
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }

        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : ""));

        if (!requestValidator.validate(url.toString(), params, signature)) {
            throw new TelephonySignatureInvalidException("Twilio signature mismatch for " + url);
        }
    }

    @Override
    public IncomingCallRequest parseRequest(HttpServletRequest request) {
        return IncomingCallRequest.builder()
                .callId(request.getParameter("CallSid"))
                .fromNumber(request.getParameter("From"))
                .toNumber(request.getParameter("To"))
                .callStatus(request.getParameter("CallStatus"))
                .build();
    }

    @Override
    public TelephonyResponse buildStreamHandoff(StreamHandoff handoff) {
        Stream stream = new Stream.Builder()
                .url(handoff.getWsUrl())
                .parameter(new Parameter.Builder().name("businessId").value(handoff.getBusinessId()).build())
                .parameter(new Parameter.Builder().name("customerPhone").value(handoff.getCustomerPhone()).build())
                .build();
        Connect connect = new Connect.Builder().stream(stream).build();
        String xml = new VoiceResponse.Builder().connect(connect).build().toXml();
        return new TelephonyResponse(xml, MediaType.APPLICATION_XML);
    }

    @Override
    public TelephonyResponse buildUnknownNumberResponse() {
        Say say = new Say.Builder(
                "We're sorry, this number is not currently in service. Goodbye.").build();
        String xml = new VoiceResponse.Builder()
                .say(say)
                .hangup(new Hangup.Builder().build())
                .build()
                .toXml();
        return new TelephonyResponse(xml, MediaType.APPLICATION_XML);
    }
}
