package com.aiassistant.summary.exceptions;

/** Generic wrapper for failures calling any downstream HTTP service. */
public class DownstreamServiceException extends RuntimeException {
    public DownstreamServiceException(String message) { super(message); }
    public DownstreamServiceException(String message, Throwable cause) { super(message, cause); }
}
