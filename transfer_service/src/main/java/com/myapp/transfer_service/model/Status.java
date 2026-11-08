package com.myapp.transfer_service.model;

public enum Status {
    PENDING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
