package com.myapp.account_service.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.myapp.account_service.model.Account;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal getBalanceSum();
}
