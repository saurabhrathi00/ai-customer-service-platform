package com.aiassistant.auth.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthFailedException extends AppException {
    public AuthFailedException(String message) {
        super(message);
    }
}
