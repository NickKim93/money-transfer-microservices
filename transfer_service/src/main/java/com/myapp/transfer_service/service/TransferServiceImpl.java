package com.myapp.transfer_service.service;

import com.myapp.transfer_service.client.AccountClient;
import com.myapp.transfer_service.dto.external.ExternalAccountResponse;
import com.myapp.transfer_service.dto.request.TransferRequestDto;
import com.myapp.transfer_service.dto.response.TransferCreateResponseDto;
import com.myapp.transfer_service.dto.response.TransferResponseDto;
import com.myapp.transfer_service.exception.TransferInvalidException;
import com.myapp.transfer_service.exception.TransferNotFoundException;
import com.myapp.transfer_service.messaging.TransferRequestedPublisher;
import com.myapp.transfer_service.messaging.status.TransferOutcome;
import com.myapp.transfer_service.model.Transfer;
import com.myapp.transfer_service.repository.TransferRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountClient accountClient;

    private final TransferRepository transferRepository;

    private final TransferStatusService transferStatusService;

    private final TransferRequestedPublisher transferRequestedPublisher;

    @Override
    public ExternalAccountResponse findById(UUID id) {
        return accountClient.getAccountById(id);
    }

    @Override
    public TransferResponseDto findTransferById(UUID id) {
        return TransferResponseDto.fromEntity(
                transferRepository
                        .findById(id)
                        .orElseThrow(() -> new TransferNotFoundException(id)));
    }

    @Override
    public TransferCreateResponseDto createTransfer(TransferRequestDto transferRequestDto) {
        if (transferRequestDto.fromAccountId().equals(transferRequestDto.toAccountId())) {
            throw new TransferInvalidException("Cannot transfer to the same account");
        }

        Transfer transfer = transferStatusService.createPending(transferRequestDto);
        transferRequestedPublisher.publishTransferRequested(transfer);

        return TransferCreateResponseDto.fromEntity(transfer);
    }

    @Override
    public void updateTransferStatus(
            UUID transferId, String failureReason, TransferOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("Transfer outcome is required");
        }
        if (outcome == TransferOutcome.COMPLETED) {
            transferStatusService.markCompleted(transferId);
        } else {
            transferStatusService.markFailed(transferId, failureReason);
        }
    }
}
