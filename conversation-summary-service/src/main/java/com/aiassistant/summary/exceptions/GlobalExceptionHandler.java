package com.aiassistant.summary.exceptions;

import com.aiassistant.summary.models.error.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return new ResponseEntity<>(new ApiError(HttpStatus.NOT_FOUND, ex.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<ApiError> handleLlm(LlmException ex) {
        HttpStatus status = "PROVIDER_NOT_CONFIGURED".equals(ex.getCode())
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        Map<String, String> fields = new LinkedHashMap<>();
        if (ex.getCode() != null) fields.put("code", ex.getCode());
        return new ResponseEntity<>(new ApiError(status, ex.getMessage(), fields), status);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleApp(AppException ex) {
        return new ResponseEntity<>(new ApiError(HttpStatus.BAD_REQUEST, ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return new ResponseEntity<>(
                new ApiError(HttpStatus.BAD_REQUEST, "Validation failed", fields),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        String msg = ex.getMessage() == null ? "Authentication required" : ex.getMessage();
        return new ResponseEntity<>(new ApiError(HttpStatus.UNAUTHORIZED, msg), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        String msg = ex.getMessage() == null ? "Access denied" : ex.getMessage();
        return new ResponseEntity<>(new ApiError(HttpStatus.FORBIDDEN, msg), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAll(Exception ex) {
        log.error("Unhandled exception", ex);
        return new ResponseEntity<>(
                new ApiError(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
