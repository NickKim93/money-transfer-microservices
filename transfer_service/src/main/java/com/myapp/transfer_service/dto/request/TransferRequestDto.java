package com.myapp.transfer_service.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequestDto (
    @NotNull
    UUID fromAccountId,
    @NotNull
    UUID toAccountId,
    @Positive
    @NotNull
    BigDecimal amount
){}
