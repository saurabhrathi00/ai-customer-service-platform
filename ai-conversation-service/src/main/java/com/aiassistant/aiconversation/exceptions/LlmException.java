package com.aiassistant.aiconversation.exceptions;

public class LlmException extends AppException {
    private final String code;

    public LlmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LlmException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() { return code; }
}
