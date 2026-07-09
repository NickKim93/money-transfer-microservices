package com.myapp.transfer_service.exception;

public class ExternalServiceException extends RuntimeException{
    public ExternalServiceException() {
        super("External Service error");
    }
}
