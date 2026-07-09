package com.myapp.account_service.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DepositRequestDto(
    @NotNull
    @Positive
    BigDecimal amount
) {}
