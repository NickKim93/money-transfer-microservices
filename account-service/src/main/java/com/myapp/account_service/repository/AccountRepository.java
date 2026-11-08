package com.myapp.account_service.repository;

import com.myapp.account_service.model.Account;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    @Query("select coalesce(sum(a.balance), 0) from Account a")
    BigDecimal getBalanceSum();
}
