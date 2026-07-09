package com.myapp.account_service.exception;

import java.util.UUID;

public class OptimisticLockingFailureException extends RuntimeException{
    public OptimisticLockingFailureException (UUID id) {
        super("Cannot proceed with operation ID: " + id);
    }
}
