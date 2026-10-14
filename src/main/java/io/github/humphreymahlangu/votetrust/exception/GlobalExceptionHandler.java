package io.github.humphreymahlangu.votetrust.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.github.humphreymahlangu.votetrust.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateResourceException.class)
    ResponseEntity<ApiErrorResponse> handleDuplicateResource(
            DuplicateResourceException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "Request conflicts with existing persisted data", request.getRequestURI());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(EligibilityException.class)
    ResponseEntity<ApiErrorResponse> handleEligibility(
            EligibilityException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(RegistrationClosedException.class)
    ResponseEntity<ApiErrorResponse> handleRegistrationClosed(
            RegistrationClosedException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(VotingClosedException.class)
    ResponseEntity<ApiErrorResponse> handleVotingClosed(
            VotingClosedException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidVotingCredentialException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidVotingCredential(
            InvalidVotingCredentialException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ResultsUnavailableException.class)
    ResponseEntity<ApiErrorResponse> handleResultsUnavailable(
            ResultsUnavailableException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(ElectionLifecycleException.class)
    ResponseEntity<ApiErrorResponse> handleElectionLifecycle(
            ElectionLifecycleException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AdminBootstrapException.class)
    ResponseEntity<ApiErrorResponse> handleAdminBootstrap(
            AdminBootstrapException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidBootstrapTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidBootstrapToken(
            InvalidBootstrapTokenException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(InvalidBallotTokenException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidBallotToken(
            InvalidBallotTokenException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String parameterName = exception.getName();
        String message = "Invalid value for '%s'".formatted(parameterName);
        Class<?> requiredType = exception.getRequiredType();
        if (requiredType != null) {
            message = "Invalid %s value for '%s'".formatted(requiredType.getSimpleName(), parameterName);
        }
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        if (exception.getMostSpecificCause() instanceof InvalidFormatException invalidFormatException) {
            return error(
                    HttpStatus.BAD_REQUEST,
                    invalidFormatMessage(invalidFormatException),
                    request.getRequestURI()
            );
        }
        return error(HttpStatus.BAD_REQUEST, "Malformed JSON request", request.getRequestURI());
    }

    private String invalidFormatMessage(InvalidFormatException exception) {
        String fieldName = exception.getPath().isEmpty()
                ? "request body"
                : exception.getPath().getLast().getFieldName();
        Class<?> targetType = exception.getTargetType();

        if (targetType != null && targetType.isEnum()) {
            String allowedValues = Arrays.stream(targetType.getEnumConstants())
                    .map(String::valueOf)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return "Invalid value for '%s'. Allowed values: %s".formatted(fieldName, allowedValues);
        }

        return "Invalid value for '%s'".formatted(fieldName);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), message, path));
    }
}
