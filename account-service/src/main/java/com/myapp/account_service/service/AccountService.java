package com.myapp.account_service.service;

import java.math.BigDecimal;
import java.util.UUID;

import com.myapp.account_service.dto.request.AccountCreateRequestDTO;
import com.myapp.account_service.dto.request.DepositRequestDto;
import com.myapp.account_service.dto.request.WithdrawRequestDto;
import com.myapp.account_service.dto.response.AccountResponseDTO;
import com.myapp.account_service.dto.response.BalanceSumResponseDTO;

public interface AccountService {
    AccountResponseDTO getAccountById(UUID accountId);
    BalanceSumResponseDTO getBalanceSum();
    AccountResponseDTO createAccount(AccountCreateRequestDTO accountRequestDTO);
    AccountResponseDTO withdraw(UUID accountId, WithdrawRequestDto withdrawRequestDto);
    AccountResponseDTO deposit(UUID accountId, DepositRequestDto depositRequestDto);
    AccountResponseDTO transfer(UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount);
}
