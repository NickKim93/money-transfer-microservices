package com.myapp.account_service.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myapp.account_service.dto.request.AccountCreateRequestDTO;
import com.myapp.account_service.dto.request.DepositRequestDto;
import com.myapp.account_service.dto.request.WithdrawRequestDto;
import com.myapp.account_service.dto.response.AccountResponseDTO;
import com.myapp.account_service.dto.response.BalanceSumResponseDTO;
import com.myapp.account_service.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponseDTO> getAccountById(@PathVariable UUID id){
        return ResponseEntity.ok().body(accountService.getAccountById(id));
    }
    
    @GetMapping("/accounts/balance_sum")
    public ResponseEntity<BalanceSumResponseDTO> getBalanceSum() {
        return ResponseEntity.ok().body(accountService.getBalanceSum());
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponseDTO> createAccount(@RequestBody @Valid AccountCreateRequestDTO accountCreateRequestDTO) {
        AccountResponseDTO createdAccount = accountService.createAccount(accountCreateRequestDTO);
        URI location = URI.create("/api/v1/accounts/" + createdAccount.id());
        return ResponseEntity.
            created(location).
            body(createdAccount);
    }

    @PostMapping("/accounts/{id}/withdraw")
    public ResponseEntity<AccountResponseDTO> withdraw(
        @RequestBody @Valid WithdrawRequestDto withdrawRequestDto,
        @PathVariable UUID id
    ) {
        AccountResponseDTO updatedAccount = accountService.withdraw(id, withdrawRequestDto);
        return ResponseEntity.ok().body(updatedAccount);
    }

     @PostMapping("/accounts/{id}/deposit")
    public ResponseEntity<AccountResponseDTO> deposit(
        @RequestBody @Valid DepositRequestDto depositRequestDto,
        @PathVariable UUID id
    ) {
        AccountResponseDTO updatedAccount = accountService.deposit(id, depositRequestDto);
        return ResponseEntity.ok().body(updatedAccount);
    }
}
