package com.myapp.account_service.dto.response;

import com.myapp.account_service.model.Account;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponseDTO(
        UUID id,
        String currency,
        String ownerName,
        BigDecimal balance,
        Instant createdAt,
        Instant updatedAt) {
    public static AccountResponseDTO fromEntity(Account account) {
        return new AccountResponseDTO(
                account.getId(),
                account.getCurrency(),
                account.getOwnerName(),
                account.getBalance(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
