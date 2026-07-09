package com.myapp.transfer_service.exception;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.myapp.transfer_service.client.exception.AccountClientException;
import com.myapp.transfer_service.client.exception.AccountClientInsufficientFundsException;
import com.myapp.transfer_service.client.exception.AccountClientNotFoundException;
import com.myapp.transfer_service.client.exception.AccountClientResourceAccessException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountClientNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
        AccountClientNotFoundException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }

    @ExceptionHandler(AccountClientInsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
        AccountClientInsufficientFundsException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);
        }

    @ExceptionHandler(AccountClientResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleAccountUnavailable(
        AccountClientResourceAccessException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
        }

    @ExceptionHandler(AccountClientException.class)
    public ResponseEntity<ErrorResponse> handleAccountServerError(
        AccountClientException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
        }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTransferNotFoundException(
        TransferNotFoundException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
        }

    @ExceptionHandler(TransferInvalidException.class)
    public ResponseEntity<ErrorResponse> handleTransferInvalid(
        TransferInvalidException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
        }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceError(
        ExternalServiceException ex,
        HttpServletRequest request) {
            return buildErrorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
        }

    @ExceptionHandler(TransferProcessingException.class)
    public ResponseEntity<ErrorResponse> handleTransferProcessing(
        TransferProcessingException ex,
        HttpServletRequest request) {
            return buildErrorResponse(
                ex.getStatus(),
                ex.getMessage(),
                request,
                ex.getTransferId()
            );
        }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String message,
        HttpServletRequest request
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            message,
            status.getReasonPhrase(),
            request.getRequestURI(),
            status.value(),
            null
        );

        return ResponseEntity.status(status).body(errorResponse);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
        HttpStatus status,
        String message,
        HttpServletRequest request,
        UUID transferId
    ) {
        ErrorResponse errorResponse = new ErrorResponse(
            Instant.now(),
            message,
            status.getReasonPhrase(),
            request.getRequestURI(),
            status.value(),
            transferId
        );

        return ResponseEntity.status(status).body(errorResponse);
    }
}
