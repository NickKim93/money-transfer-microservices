package com.myapp.transfer_service.messaging.dto;

import java.time.Instant;
import java.util.UUID;

import com.myapp.transfer_service.messaging.status.TransferOutcome;

public record TransferResultMessage (
    UUID eventId,
    UUID transferId,
    TransferOutcome outcome,
    String failureReason,
    Instant occurredAt
) {}
