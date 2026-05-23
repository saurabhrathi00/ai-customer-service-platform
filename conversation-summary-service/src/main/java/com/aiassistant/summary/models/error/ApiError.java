package com.aiassistant.summary.models.error;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Data
@AllArgsConstructor
public class ApiError {
    private HttpStatus status;
    private String message;
    private Map<String, String> fieldErrors;

    public ApiError(HttpStatus status, String message) {
        this(status, message, null);
    }
}