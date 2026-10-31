package com.cricauction.common.exception;

import com.cricauction.common.api.ApiError;
import com.cricauction.common.api.ApiResponse;
import com.cricauction.common.api.ErrorDetail;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("Application exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        ApiError error = ApiError.of(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.add(ErrorDetail.builder()
                    .field(fieldError.getField())
                    .issue(fieldError.getDefaultMessage())
                    .build());
        }

        log.warn("Validation failed for request: {}", details);
        ApiError error = ApiError.of("VALIDATION_FAILED", "One or more validation constraints failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<ErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "";
            String field = propertyPath.contains(".") ? propertyPath.substring(propertyPath.lastIndexOf('.') + 1) : propertyPath;
            details.add(ErrorDetail.builder()
                    .field(field)
                    .issue(violation.getMessage())
                    .build());
        }

        log.warn("Constraint violation failed for request: {}", details);
        ApiError error = ApiError.of("VALIDATION_FAILED", "One or more validation constraints failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        List<ErrorDetail> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> {
                details.add(ErrorDetail.builder()
                        .field(paramName != null ? paramName : "parameter")
                        .issue(error.getDefaultMessage())
                        .build());
            });
        });

        log.warn("Handler method validation failed for request: {}", details);
        ApiError error = ApiError.of("VALIDATION_FAILED", "One or more validation constraints failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Resource not found: {}", ex.getResourcePath());
        ApiError error = ApiError.of("NOT_FOUND", "Resource not found: " + ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName();
        String issue = String.format("Parameter '%s' should be of type %s", field, ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "valid type");
        ErrorDetail detail = ErrorDetail.builder()
                .field(field)
                .issue(issue)
                .build();

        log.warn("Argument type mismatch: {}", detail);
        ApiError error = ApiError.of("INVALID_PARAMETER", "Invalid parameter type provided", List.of(detail));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ApiError error = ApiError.of("INVALID_CREDENTIALS", "Invalid email/mobile or password");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(error));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiError error = ApiError.of("FORBIDDEN", "You do not have permission to access this resource");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(error));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Database constraint or unique key violation: {}", ex.getMessage());
        ApiError error = ApiError.of("RESOURCE_CONFLICT",
                "A resource with these details already exists or a uniqueness constraint was violated.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLockFailure(Exception ex) {
        log.warn("Optimistic concurrency conflict detected: {}", ex.getMessage());
        ApiError error = ApiError.of("CONCURRENT_MODIFICATION",
                "The resource was modified concurrently by another transaction. Please refresh and try again.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unhandled internal server error: ", ex);
        ApiError error = ApiError.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(error));
    }
}
