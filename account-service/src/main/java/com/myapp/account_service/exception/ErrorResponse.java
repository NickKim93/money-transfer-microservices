package com.myapp.account_service.exception;

import java.time.Instant;

public record ErrorResponse(
    Instant timestamp,
    String message,
    String error,
    String path,
    int status) {}
