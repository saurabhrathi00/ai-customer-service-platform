package com.aiassistant.incomingcall.provider;

import lombok.Value;
import org.springframework.http.MediaType;

/** Provider-built response body + the content-type the vendor expects to receive. */
@Value
public class TelephonyResponse {
    String body;
    MediaType contentType;
}
