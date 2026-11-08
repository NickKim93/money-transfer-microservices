package com.myapp.transfer_service.messaging.dto;

import com.myapp.transfer_service.messaging.status.TransferOutcome;
import java.time.Instant;
import java.util.UUID;

public record TransferResultMessage(
        UUID eventId,
        UUID transferId,
        TransferOutcome outcome,
        String failureReason,
        Instant occurredAt) {}
