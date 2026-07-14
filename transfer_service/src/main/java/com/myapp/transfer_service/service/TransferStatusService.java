package com.myapp.transfer_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.myapp.transfer_service.dto.request.TransferRequestDto;
import com.myapp.transfer_service.exception.TransferNotFoundException;
import com.myapp.transfer_service.model.Status;
import com.myapp.transfer_service.model.Transfer;
import com.myapp.transfer_service.repository.TransferRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransferStatusService {
    
    private final TransferRepository transferRepository;

    @Transactional
    public Transfer createPending(TransferRequestDto request) {
        Transfer transfer = Transfer
            .builder()
            .fromAccountId(request.fromAccountId())
            .toAccountId(request.toAccountId())
            .status(Status.PENDING)
            .amount(request.amount())
            .build();
        
        return transferRepository.save(transfer);
    }

    @Transactional
    public Transfer markCompleted(UUID transferId) {
        Transfer transfer = getTransferOrThrow(transferId);
        transfer.setStatus(Status.COMPLETED);
        return transferRepository.save(transfer);
    }

    @Transactional
    public Transfer markFailed(UUID transferId, String failReason) {
        Transfer transfer = getTransferOrThrow(transferId);
        if (transfer.getStatus() == Status.COMPLETED) {
            throw new IllegalStateException("Cannot mark a completed transfer as failed.");
        }
        transfer.setStatus(Status.FAILED);
        transfer.setFailureReason(failReason);
        return transferRepository.save(transfer);
    }

    public Transfer getTransferOrThrow(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }
}
