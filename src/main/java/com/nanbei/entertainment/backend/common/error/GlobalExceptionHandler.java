package com.nanbei.entertainment.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        return build(
                exception.code(),
                exception.getMessage(),
                request.getRequestURI(),
                exception.properties());
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MissingRequestHeaderException.class
    })
    ResponseEntity<ProblemDetail> handleValidation(Exception exception, HttpServletRequest request) {
        return build(
                ErrorCode.VALIDATION_FAILED,
                "请求参数不符合要求",
                request.getRequestURI(),
                Map.of());
    }

    private ResponseEntity<ProblemDetail> build(
            ErrorCode code, String detail, String instance, Map<String, Object> properties) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.name());
        problem.setType(URI.create("urn:nanbei:error:" + code.name().toLowerCase()));
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", code.name());
        problem.setProperty("traceId", UUID.randomUUID().toString());
        properties.forEach(problem::setProperty);
        return ResponseEntity.status(code.status())
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
