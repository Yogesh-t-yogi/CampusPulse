package com.campuspulse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Turns exceptions into one JSON shape: {"status": 400, "message": "...", "error": "..."}.
 * "error" repeats the message because existing frontend code reads either key.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        String message = e.getReason() == null ? "Request failed." : e.getReason();
        return body(e.getStatusCode(), message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Invalid request.");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadInput(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, safe(e.getMessage(), "Invalid request."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large (max 10 MB).");
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<Map<String, Object>> handleUnreachable(WebClientRequestException e) {
        log.warn("Upstream service unreachable: {}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE,
                "An AI service is not reachable. Check that the AI service (port 8000) is running.");
    }

    // Our own "service not ready / AI provider problem" errors carry a readable message.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleUnavailable(IllegalStateException e) {
        log.warn("Service problem: {}", e.getMessage());
        return body(HttpStatus.SERVICE_UNAVAILABLE, safe(e.getMessage(), "Service unavailable."));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDatabase(DataAccessException e) {
        log.error("Database error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred.");
    }

    // AI parsing failures etc. are plain RuntimeExceptions with messages we wrote.
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleOther(RuntimeException e) {
        log.error("Unhandled error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                safe(e.getMessage(), "Something went wrong. Please try again."));
    }

    private static String safe(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "message", message,
                "error", message));
    }
}
