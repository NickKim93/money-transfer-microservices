package com.myapp.account_service.messaging.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferRequestedMessage(
    UUID eventId,
    UUID transferId,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    Instant occurredAt
) {}
