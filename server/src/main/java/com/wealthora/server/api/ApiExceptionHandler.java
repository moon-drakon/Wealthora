package com.wealthora.server.api;

import java.time.Clock;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                exception.getCode(), exception.getMessage(), clock.instant()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": "
                        + error.getDefaultMessage())
                .distinct().collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", message, clock.instant()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            ConstraintViolationException.class})
    ResponseEntity<ApiError> handleMalformed(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(
                "MALFORMED_REQUEST", "The request contains an invalid value.",
                clock.instant()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleConflict(
            DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
                "FINANCE_CONFLICT",
                "The requested finance change conflicts with stored data.",
                clock.instant()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR",
                        "The request could not be completed safely.",
                        clock.instant()));
    }
}
