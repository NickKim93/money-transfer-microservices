package com.myapp.transfer_service.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        String message,
        String error,
        String path,
        int status,
        UUID transferId) {}
