package com.myapp.transfer_service.exception;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    String message,
    String error,
    String path,
    int status,
    UUID transferId
) {}