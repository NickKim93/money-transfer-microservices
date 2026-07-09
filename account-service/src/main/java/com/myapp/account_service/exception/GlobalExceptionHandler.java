package com.myapp.account_service.exception;
import java.time.Instant;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFoundException(
        AccountNotFoundException ex,
        HttpServletRequest request) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ErrorResponse(
                    Instant.now(),
                    ex.getMessage(),
                    HttpStatus.NOT_FOUND.getReasonPhrase(),
                    request.getRequestURI(),
                    HttpStatus.NOT_FOUND.value()
                )
            );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException ex,
        HttpServletRequest request) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(
                    Instant.now(),
                    ex.getMessage(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    request.getRequestURI(),
                    HttpStatus.BAD_REQUEST.value()
                )
            );
        }
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(
        InsufficientFundsException ex,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
            new ErrorResponse(
                Instant.now(),
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                request.getRequestURI(),
                HttpStatus.UNPROCESSABLE_ENTITY.value()
            )
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailureException(
        OptimisticLockingFailureException ex,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            new ErrorResponse(
                Instant.now(),
                ex.getMessage(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                request.getRequestURI(),
                HttpStatus.CONFLICT.value()
            )
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
        IllegalArgumentException ex,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            new ErrorResponse(
                Instant.now(),
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                request.getRequestURI(),
                HttpStatus.BAD_REQUEST.value()
            )
        );
    }

}
