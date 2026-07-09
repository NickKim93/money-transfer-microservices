package com.myapp.account_service.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.myapp.account_service.exception.InsufficientFundsException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Account extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String ownerName;

    @Column(precision = 19, scale = 4)
    private BigDecimal balance;

    private String currency;

    @Version
    private Long version;

    public void withdraw(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(id);
        }
        this.balance = balance.subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        this.balance = balance.add(amount);
    }

}
