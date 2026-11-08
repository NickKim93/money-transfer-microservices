package com.myapp.account_service.exception;

import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(UUID id) {
        super("Not enough balance to proceed with operation with ID: " + id);
    }
}
