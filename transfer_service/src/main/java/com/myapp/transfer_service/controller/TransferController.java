package com.myapp.transfer_service.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myapp.transfer_service.dto.external.ExternalAccountResponse;
import com.myapp.transfer_service.dto.request.TransferRequestDto;
import com.myapp.transfer_service.dto.response.TransferCreateResponseDto;
import com.myapp.transfer_service.dto.response.TransferResponseDto;
import com.myapp.transfer_service.service.TransferService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class TransferController {

    private final TransferService transferService;

    @GetMapping("/transfers/check/{id}")
    public ResponseEntity<ExternalAccountResponse> getClientAccount(@PathVariable UUID id) {
        return ResponseEntity.ok().body(transferService.findById(id));
    }

    @GetMapping("/transfers/{id}")
    public ResponseEntity<TransferResponseDto> getTransferStatus(@PathVariable UUID id) {
        return ResponseEntity.ok().body(transferService.findTransferById(id));
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferCreateResponseDto> makeTransfer(@RequestBody @Valid TransferRequestDto transferRequestDto) {
        return ResponseEntity.accepted().body(transferService.createTransfer(transferRequestDto));
    }
    
}
