package com.cosmetics.ecommerce.config;

import com.cosmetics.ecommerce.exception.*;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    record ErrorResponse(boolean success, String message, int status, Instant timestamp) {}

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException e) {
        return error(e.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({BadRequestException.class, InvalidCouponException.class, InsufficientStockException.class, DuplicateResourceException.class, OrderCancellationException.class, IllegalArgumentException.class})
    ResponseEntity<ErrorResponse> badRequest(RuntimeException e) {
        return error(e.getMessage() != null ? e.getMessage() : "Invalid request data.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorResponse> maxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return error("Uploaded file exceeds the maximum allowed size limit of 5MB.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> messageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        return error("Malformed request body or invalid JSON format.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class, AuthenticationException.class})
    ResponseEntity<ErrorResponse> unauthorized(Exception e) {
        return error(e.getMessage() != null ? e.getMessage() : "Authentication failed.", HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponse> forbidden() {
        return error("You do not have permission to perform this action.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getField() + ": " + field.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({ConstraintViolationException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorResponse> data(RuntimeException e) {
        log.warn("Database constraint or integrity violation: {}", e.getMessage());
        return error("The request conflicts with existing data or validation rules.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        log.error("Unhandled server exception: ", e);
        return error("An unexpected server error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> error(String message, HttpStatus status) {
        return ResponseEntity.status(status).body(new ErrorResponse(false, message, status.value(), Instant.now()));
    }
}