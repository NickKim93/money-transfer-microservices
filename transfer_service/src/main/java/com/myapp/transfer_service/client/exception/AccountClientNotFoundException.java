package com.myapp.transfer_service.client.exception;

public class AccountClientNotFoundException  extends RuntimeException{
    public AccountClientNotFoundException(String message) {
        super(message);
    }
}
