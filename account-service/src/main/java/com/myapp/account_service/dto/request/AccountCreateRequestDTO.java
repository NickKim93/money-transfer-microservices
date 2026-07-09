package com.myapp.account_service.dto.request;

import java.math.BigDecimal;

import com.myapp.account_service.model.Account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountCreateRequestDTO(
    @NotBlank
    String ownerName,
    @NotNull
    BigDecimal balance,
    @NotBlank
    String currency
) {
    public static AccountCreateRequestDTO fromEntity(Account account) {
        return new AccountCreateRequestDTO(
            account.getOwnerName(),
            account.getBalance(),
            account.getCurrency()
        );
    }
}
