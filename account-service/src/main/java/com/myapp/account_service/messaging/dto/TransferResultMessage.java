package com.myapp.account_service.messaging.dto;

import java.time.Instant;
import java.util.UUID;

import com.myapp.account_service.messaging.status.TransferOutcome;

public record TransferResultMessage(
    UUID eventId,
    UUID transferId,
    TransferOutcome outcome,
    String failureReason,
    Instant occurredAt
) {}