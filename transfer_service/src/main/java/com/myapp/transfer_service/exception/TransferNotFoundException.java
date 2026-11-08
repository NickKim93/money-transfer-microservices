package com.myapp.transfer_service.exception;

import java.util.UUID;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(UUID id) {
        super("Transfer not found for ID: " + id);
    }
}
