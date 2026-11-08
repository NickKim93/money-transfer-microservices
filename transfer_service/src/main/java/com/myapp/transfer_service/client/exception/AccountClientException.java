package com.myapp.transfer_service.client.exception;

public class AccountClientException extends RuntimeException {
    public AccountClientException(String message) {
        super(message);
    }

    public AccountClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
