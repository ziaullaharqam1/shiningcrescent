package com.risingcrescent.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> bad(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", userMessage(ex.getMessage(),
                "Please check the details you entered and try again.")));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", userMessage(ex.getMessage(),
                "That action cannot be completed right now.")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> inUse(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "This record is still in use and cannot be deleted."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have access to this."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        String field = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField())
                .findFirst()
                .orElse("");
        String msg = switch (field) {
            case "username" -> "Enter a username.";
            case "password" -> "Enter a password.";
            case "email" -> "Enter a valid email.";
            case "fullName" -> "Enter your name.";
            case "phone" -> "Enter a mobile number.";
            case "code" -> "Enter the 6-digit code.";
            default -> "Please fill in all required fields.";
        };
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Please fill in all required fields."));
    }

    @ExceptionHandler(org.springframework.transaction.UnexpectedRollbackException.class)
    public ResponseEntity<Map<String, String>> rollback(org.springframework.transaction.UnexpectedRollbackException ex) {
        log.error("Payment or order update rolled back", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                "The order could not be finished after payment. Please open My orders and try again, or contact us if the card was charged."));
    }
    public ResponseEntity<Map<String, String>> other(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Something went wrong on our side. Please try again."));
    }

    private static String userMessage(String raw, String fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String lower = raw.toLowerCase();
        if (lower.contains("exception") || lower.contains("sql") || lower.contains("jdbc")
                || lower.contains("stack") || raw.length() > 180) {
            return fallback;
        }
        return raw;
    }
}
