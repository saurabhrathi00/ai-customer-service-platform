package com.aiassistant.incomingcall.exceptions;

public class DownstreamServiceException extends AppException {
    public DownstreamServiceException(String message) {
        super(message);
    }

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
