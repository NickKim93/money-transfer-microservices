package com.myapp.account_service.web;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp, String message, String error, String path, int status) {}
