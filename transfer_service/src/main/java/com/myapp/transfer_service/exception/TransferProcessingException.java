package com.myapp.transfer_service.exception;

import java.util.UUID;

import org.springframework.http.HttpStatus;

public class TransferProcessingException extends RuntimeException {
    private final UUID transferId;
    private final HttpStatus status;

    public TransferProcessingException(
        UUID transferId,
        HttpStatus status,
        String message,
        Throwable cause
    ) {
        super(message, cause);
        this.transferId = transferId;
        this.status = status;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
