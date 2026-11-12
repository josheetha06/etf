package com.epictalenttrust.hr.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Turns exceptions into RFC 9457 problem responses with a stable "code" field. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ErrorLogWriter errorLog;

    public ApiExceptionHandler(ErrorLogWriter errorLog) {
        this.errorLog = errorLog;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "validation_failed",
                "Some fields need attention.");
        response.getBody().setProperty("fields", fields);
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> handleUnreadable(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad_request", "The request could not be read.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> handleTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "file_too_large", "Each CV can be up to 10 MB.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Your role can't do this.");
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleUnauthenticated(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Please sign in.");
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ProblemDetail> handleNotFound(Exception ex) {
        return problem(HttpStatus.NOT_FOUND, "not_found", "Not found.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        errorLog.record("error", "api", ex,
                "{\"method\":\"" + request.getMethod() + "\",\"path\":\"" + request.getRequestURI().replace("\"", "") + "\"}");
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error",
                "Something went wrong on our side. Please try again.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setProperty("code", code);
        String requestId = RequestIdFilter.current();
        if (requestId != null) {
            detail.setProperty("requestId", requestId);
        }
        return ResponseEntity.status(status).body(detail);
    }
}
