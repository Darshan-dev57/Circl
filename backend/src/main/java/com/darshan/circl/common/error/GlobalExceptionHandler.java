package com.darshan.circl.common.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Every error leaves the API as RFC 9457 Problem Details:
 * type, title, status, detail, instance (+ errors for validation).
 * Stack traces never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://circl.dev/problems/";

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        pd.setType(URI.create(TYPE_BASE + ex.code()));
        pd.setTitle(ex.status().getReasonPhrase());
        ResponseEntity.BodyBuilder res = ResponseEntity.status(ex.status());
        if (ex instanceof TooManyRequestsException tooMany) {
            res.header(HttpHeaders.RETRY_AFTER, String.valueOf(tooMany.retryAfterSeconds()));
        }
        return res.body(pd);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleStale(OptimisticLockingFailureException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Someone changed this at the same time, please retry");
        pd.setType(URI.create(TYPE_BASE + "concurrent-update"));
        return pd;
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    ProblemDetail handleRedisDown(RedisConnectionFailureException ex) {
        log.warn("Redis unavailable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "This feature is temporarily unavailable");
        pd.setType(URI.create(TYPE_BASE + "unavailable"));
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraint(ConstraintViolationException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request has invalid values");
        pd.setType(URI.create(TYPE_BASE + "validation"));
        pd.setTitle("Validation failed");
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", lastNode(v.getPropertyPath().toString()), "message", v.getMessage()))
                .toList();
        pd.setProperty("errors", errors);
        return pd;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request has invalid fields");
        pd.setType(URI.create(TYPE_BASE + "validation"));
        pd.setTitle("Validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(e -> Map.of(
                        "field", e instanceof FieldError fe ? fe.getField() : e.getObjectName(),
                        "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        pd.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not allowed to do this");
        pd.setType(URI.create(TYPE_BASE + "forbidden"));
        return pd;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong on our side");
        pd.setType(URI.create(TYPE_BASE + "internal"));
        return pd;
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
