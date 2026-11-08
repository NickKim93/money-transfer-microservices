package com.myapp.transfer_service.service;

import com.myapp.transfer_service.dto.external.ExternalAccountResponse;
import com.myapp.transfer_service.dto.request.TransferRequestDto;
import com.myapp.transfer_service.dto.response.TransferCreateResponseDto;
import com.myapp.transfer_service.dto.response.TransferResponseDto;
import com.myapp.transfer_service.messaging.status.TransferOutcome;
import java.util.UUID;

public interface TransferService {
    ExternalAccountResponse findById(UUID accountId);

    TransferResponseDto findTransferById(UUID transferId);

    TransferCreateResponseDto createTransfer(TransferRequestDto transferRequestDto);

    void updateTransferStatus(UUID transferId, String failureReason, TransferOutcome outcome);
}
