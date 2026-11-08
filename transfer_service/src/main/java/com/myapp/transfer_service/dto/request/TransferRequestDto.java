package com.myapp.transfer_service.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequestDto(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @Positive @NotNull @Digits(integer = 15, fraction = 4) BigDecimal amount) {}
