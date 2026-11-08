package com.myapp.account_service.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record WithdrawRequestDto(
        @NotNull @Positive @Digits(integer = 15, fraction = 4) BigDecimal amount) {}
