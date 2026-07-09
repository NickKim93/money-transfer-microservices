package com.myapp.account_service.service;

import org.springframework.stereotype.Service;

import com.myapp.account_service.dto.request.AccountCreateRequestDTO;
import com.myapp.account_service.dto.response.AccountResponseDTO;
import com.myapp.account_service.dto.response.BalanceSumResponseDTO;
import com.myapp.account_service.exception.AccountNotFoundException;
import com.myapp.account_service.model.Account;
import com.myapp.account_service.repository.AccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigDecimal;

import com.myapp.account_service.dto.request.WithdrawRequestDto;
import com.myapp.account_service.dto.request.DepositRequestDto;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;


    public AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public AccountResponseDTO getAccountById(UUID id) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));

        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    public BalanceSumResponseDTO getBalanceSum() {
        return new BalanceSumResponseDTO(accountRepository.getBalanceSum());
    }

    @Override
    public AccountResponseDTO createAccount(AccountCreateRequestDTO accountCreateRequestDTO) {

        Account account = Account.builder()
                .ownerName(accountCreateRequestDTO.ownerName())
                .balance(accountCreateRequestDTO.balance())
                .currency(accountCreateRequestDTO.currency())
                .build();

        Account savedAccount = accountRepository.save(account);
        return AccountResponseDTO.fromEntity(savedAccount);
    }

    @Override
    @Transactional
    public AccountResponseDTO withdraw(UUID id, WithdrawRequestDto withdrawRequestDto) {
        Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        account.withdraw(withdrawRequestDto.amount());
        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    @Transactional
    public AccountResponseDTO deposit(UUID id, DepositRequestDto depositRequestDto) {
         Account account = accountRepository.findById(id)
            .orElseThrow(() -> new AccountNotFoundException(id));
        account.deposit(depositRequestDto.amount());
        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    @Transactional
    public AccountResponseDTO transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        Account fromAccount = accountRepository.findById(fromAccountId)
            .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount = accountRepository.findById(toAccountId)
            .orElseThrow(() -> new AccountNotFoundException(toAccountId));

        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        return AccountResponseDTO.fromEntity(fromAccount);

    }


    
}
