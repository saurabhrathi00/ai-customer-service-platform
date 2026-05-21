package com.aiassistant.callorchestration.exceptions;

import com.aiassistant.callorchestration.models.error.ApiError;
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

    @ExceptionHandler(CallNotFoundException.class)
    public ResponseEntity<ApiError> handleCallNotFound(CallNotFoundException ex) {
        return new ResponseEntity<>(new ApiError(HttpStatus.NOT_FOUND, ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DownstreamServiceException.class)
    public ResponseEntity<ApiError> handleDownstream(DownstreamServiceException ex) {
        log.error("Downstream service error: {}", ex.getMessage(), ex);
        return new ResponseEntity<>(new ApiError(HttpStatus.BAD_GATEWAY, "Downstream service unavailable"),
                HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return new ResponseEntity<>(new ApiError(HttpStatus.UNAUTHORIZED,
                ex.getMessage() == null ? "Authentication required" : ex.getMessage()),
                HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return new ResponseEntity<>(new ApiError(HttpStatus.FORBIDDEN,
                ex.getMessage() == null ? "Access denied" : ex.getMessage()),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException ex) {
        log.warn("App exception: {}", ex.getMessage());
        return new ResponseEntity<>(new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
