package com.myapp.transfer_service.exception;

public class TransferInvalidException extends RuntimeException{
    public TransferInvalidException(String message) {
        super("Transfer is not valid: " + message );
    }
}
