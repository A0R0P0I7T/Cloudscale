package com.cloudscale.audit.api;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AuditExceptionHandler {
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<AuditErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AuditErrorResponse("VALIDATION_ERROR", "Audit query parameters are invalid", Instant.now()));
    }
}
