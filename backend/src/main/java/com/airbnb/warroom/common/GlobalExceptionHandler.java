package com.airbnb.warroom.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/** Translates exceptions to {@link ApiError} JSON responses across all REST controllers. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException ex, ServerWebExchange exchange) {
        String msg = ex.getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "Bad Request", msg, exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleStatus(ResponseStatusException ex, ServerWebExchange exchange) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(ApiError.of(ex.getStatusCode().value(), "Error", ex.getReason(),
                        exchange.getRequest().getPath().value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(500, "Internal Server Error", ex.getMessage(),
                        exchange.getRequest().getPath().value()));
    }
}
