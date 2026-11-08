package com.myapp.account_service.messaging.dto;

import com.myapp.account_service.messaging.status.TransferOutcome;
import java.time.Instant;
import java.util.UUID;

public record TransferResultMessage(
        UUID eventId,
        UUID transferId,
        TransferOutcome outcome,
        String failureReason,
        Instant occurredAt) {}
