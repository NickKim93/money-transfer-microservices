package com.myapp.transfer_service.client.exception;

public class AccountClientResourceAccessException extends RuntimeException {
    public AccountClientResourceAccessException(String message) {
        super(message);
    }

    public AccountClientResourceAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
