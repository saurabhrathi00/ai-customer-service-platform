package com.aiassistant.knowledge.exceptions;

import java.util.Map;

public class ValidationFailedException extends AppException {
    private final Map<String, String> fieldErrors;

    public ValidationFailedException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
