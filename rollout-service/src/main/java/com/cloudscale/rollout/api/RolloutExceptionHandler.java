package com.cloudscale.rollout.api;

import com.cloudscale.rollout.application.RolloutNotFoundException;
import com.cloudscale.rollout.domain.InvalidRolloutTransitionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class RolloutExceptionHandler {
    @ExceptionHandler(RolloutNotFoundException.class)
    public ResponseEntity<RolloutErrorResponse> handleNotFound(RolloutNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, "ROLLOUT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidRolloutTransitionException.class)
    public ResponseEntity<RolloutErrorResponse> handleTransition(InvalidRolloutTransitionException exception) {
        return response(HttpStatus.CONFLICT, "INVALID_ROLLOUT_TRANSITION", exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RolloutErrorResponse> handleConflict(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "ROLLOUT_CONFLICT", "Another active rollout already exists");
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            ConstraintViolationException.class})
    public ResponseEntity<RolloutErrorResponse> handleValidation(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Rollout request data is invalid");
    }

    private ResponseEntity<RolloutErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new RolloutErrorResponse(code, message, Instant.now()));
    }
}
