package com.myapp.account_service.service;

import com.myapp.account_service.dto.request.AccountCreateRequestDTO;
import com.myapp.account_service.dto.request.DepositRequestDto;
import com.myapp.account_service.dto.request.WithdrawRequestDto;
import com.myapp.account_service.dto.response.AccountResponseDTO;
import com.myapp.account_service.dto.response.BalanceSumResponseDTO;
import com.myapp.account_service.exception.AccountNotFoundException;
import com.myapp.account_service.exception.InvalidAccountOperationException;
import com.myapp.account_service.model.Account;
import com.myapp.account_service.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    private final IdempotencyService idempotencyService;

    public AccountServiceImpl(
            AccountRepository accountRepository, IdempotencyService idempotencyService) {
        this.accountRepository = accountRepository;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public AccountResponseDTO getAccountById(UUID id) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));

        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    public BalanceSumResponseDTO getBalanceSum() {
        return new BalanceSumResponseDTO(accountRepository.getBalanceSum());
    }

    @Override
    @Transactional
    public AccountResponseDTO createAccount(AccountCreateRequestDTO accountCreateRequestDTO) {

        Account account =
                Account.builder()
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
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.withdraw(withdrawRequestDto.amount());
        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    @Transactional
    public AccountResponseDTO deposit(UUID id, DepositRequestDto depositRequestDto) {
        Account account =
                accountRepository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
        account.deposit(depositRequestDto.amount());
        return AccountResponseDTO.fromEntity(account);
    }

    @Override
    @Transactional
    public AccountResponseDTO transfer(
            UUID transferId, UUID fromAccountId, UUID toAccountId, BigDecimal amount) {

        if (transferId == null || fromAccountId == null || toAccountId == null) {
            throw new InvalidAccountOperationException("Transfer and account IDs are required");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidAccountOperationException("Cannot transfer to the same account");
        }
        idempotencyService.markProcessed(transferId);

        Account fromAccount =
                accountRepository
                        .findById(fromAccountId)
                        .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount =
                accountRepository
                        .findById(toAccountId)
                        .orElseThrow(() -> new AccountNotFoundException(toAccountId));

        if (!fromAccount.getCurrency().equalsIgnoreCase(toAccount.getCurrency())) {
            throw new InvalidAccountOperationException("Account currencies must match");
        }
        fromAccount.withdraw(amount);
        toAccount.deposit(amount);

        return AccountResponseDTO.fromEntity(fromAccount);
    }
}
