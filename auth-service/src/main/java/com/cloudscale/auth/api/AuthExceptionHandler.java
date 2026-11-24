package com.cloudscale.auth.api;

import com.cloudscale.auth.application.AuthConflictException;
import com.cloudscale.auth.application.InvalidCredentialsException;
import com.cloudscale.auth.application.InvalidAuthRequestException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class AuthExceptionHandler {
    @ExceptionHandler(AuthConflictException.class)
    public ResponseEntity<AuthErrorResponse> handleConflict(AuthConflictException exception) {
        return response(HttpStatus.CONFLICT, "ACCOUNT_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthErrorResponse> handleInvalidCredentials(InvalidCredentialsException exception) {
        return response(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            InvalidAuthRequestException.class})
    public ResponseEntity<AuthErrorResponse> handleInvalidRequest(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request data is invalid");
    }

    private ResponseEntity<AuthErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new AuthErrorResponse(code, message, Instant.now()));
    }
}
