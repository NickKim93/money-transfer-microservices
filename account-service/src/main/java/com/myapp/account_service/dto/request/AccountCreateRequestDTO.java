package com.myapp.account_service.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record AccountCreateRequestDTO(
        @NotBlank String ownerName,
        @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal balance,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code")
                String currency) {}
