package com.aiassistant.incomingcall.exceptions;

import com.aiassistant.incomingcall.models.error.ApiError;
import com.aiassistant.incomingcall.provider.TelephonySignatureInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TelephonySignatureInvalidException.class)
    public ResponseEntity<ApiError> handleSignatureInvalid(TelephonySignatureInvalidException ex) {
        log.warn("Telephony signature validation failed: {}", ex.getMessage());
        ApiError error = new ApiError(HttpStatus.FORBIDDEN, "Invalid telephony signature");
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BusinessNotFoundException.class)
    public ResponseEntity<ApiError> handleBusinessNotFound(BusinessNotFoundException ex) {
        ApiError error = new ApiError(HttpStatus.NOT_FOUND, ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiError> handleDownstream(DownstreamServiceException ex) {
        log.error("Downstream service error: {}", ex.getMessage(), ex);
        ApiError error = new ApiError(HttpStatus.BAD_GATEWAY, "Downstream service unavailable");
        return new ResponseEntity<>(error, HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        ApiError error = new ApiError(HttpStatus.UNAUTHORIZED,
                ex.getMessage() == null ? "Authentication required" : ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        ApiError error = new ApiError(HttpStatus.FORBIDDEN,
                ex.getMessage() == null ? "Access denied" : ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError error = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
