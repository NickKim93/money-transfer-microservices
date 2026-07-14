package com.myapp.account_service.exception;

public class DuplicateTransferMessageException extends RuntimeException {
    public DuplicateTransferMessageException(String message) {
        super("Duplicate transfer message: " + message);
    }
}
