package com.myapp.transfer_service.dto.response;

import com.myapp.transfer_service.model.Status;
import com.myapp.transfer_service.model.Transfer;
import java.util.UUID;

public record TransferResponseDto(UUID transferId, Status status, String failureReason) {
    public static TransferResponseDto fromEntity(Transfer transfer) {
        return new TransferResponseDto(
                transfer.getId(), transfer.getStatus(), transfer.getFailureReason());
    }
}
