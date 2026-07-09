package com.myapp.transfer_service.client.exception;

public class AccountClientInsufficientFundsException extends  RuntimeException {
    public AccountClientInsufficientFundsException(String message) {
        super(message);
    }
    
}
