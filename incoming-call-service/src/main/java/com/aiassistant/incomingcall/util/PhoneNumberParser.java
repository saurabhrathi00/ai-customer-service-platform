package com.aiassistant.incomingcall.util;

public final class PhoneNumberParser {

    private PhoneNumberParser() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String num = raw.strip();
        if (num.startsWith("+")) {
            num = num.substring(1);
        }
        return num;
    }
}
