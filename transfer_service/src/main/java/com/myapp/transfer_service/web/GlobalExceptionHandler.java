package com.myapp.transfer_service.web;

import com.myapp.transfer_service.client.exception.AccountClientException;
import com.myapp.transfer_service.client.exception.AccountClientIllegalArgumentException;
import com.myapp.transfer_service.client.exception.AccountClientInsufficientFundsException;
import com.myapp.transfer_service.client.exception.AccountClientNotFoundException;
import com.myapp.transfer_service.client.exception.AccountClientResourceAccessException;
import com.myapp.transfer_service.exception.TransferInvalidException;
import com.myapp.transfer_service.exception.TransferNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({TransferNotFoundException.class, AccountClientNotFoundException.class})
    ResponseEntity<ErrorResponse> notFound(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler({TransferInvalidException.class, AccountClientIllegalArgumentException.class})
    ResponseEntity<ErrorResponse> invalidTransfer(RuntimeException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccountClientInsufficientFundsException.class)
    ResponseEntity<ErrorResponse> insufficientFunds(
            AccountClientInsufficientFundsException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(AccountClientResourceAccessException.class)
    ResponseEntity<ErrorResponse> unavailable(
            AccountClientResourceAccessException ex, HttpServletRequest request) {
        log.warn("Account service unavailable requestId={}", org.slf4j.MDC.get("requestId"), ex);
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Account service is unavailable",
                request.getRequestURI());
    }

    @ExceptionHandler(AccountClientException.class)
    ResponseEntity<ErrorResponse> upstreamFailure(RuntimeException ex, HttpServletRequest request) {
        log.warn("Account service request failed", ex);
        return error(
                HttpStatus.BAD_GATEWAY, "Account service request failed", request.getRequestURI());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponse> conflict(
            OptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Concurrent update rejected path={}", request.getRequestURI());
        return error(
                HttpStatus.CONFLICT,
                "Resource was modified concurrently; retry the request",
                request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
        log.error(
                "Unexpected request failure method={} path={}",
                request.getMethod(),
                request.getRequestURI(),
                ex);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(field -> field.getField() + ": " + field.getDefaultMessage())
                        .distinct()
                        .sorted()
                        .collect(Collectors.joining("; "));
        return handleExceptionInternal(
                ex,
                message.isEmpty() ? "Request validation failed" : message,
                headers,
                status,
                request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return handleExceptionInternal(
                ex, "Malformed JSON or invalid field type", headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        String message =
                body instanceof String text
                        ? text
                        : HttpStatus.valueOf(status.value()).getReasonPhrase();
        if (status.is5xxServerError()) {
            log.error("MVC request failed path={}", path, ex);
            message = "Internal server error";
        }
        return new ResponseEntity<>(body(status, message, path), headers, status);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, String path) {
        return ResponseEntity.status(status).body(body(status, message, path));
    }

    private ErrorResponse body(HttpStatusCode status, String message, String path) {
        return new ErrorResponse(
                Instant.now(),
                message,
                HttpStatus.valueOf(status.value()).getReasonPhrase(),
                path,
                status.value(),
                null);
    }
}
