package com.myapp.transfer_service.dto.response;

import com.myapp.transfer_service.model.Transfer;
import java.util.UUID;

public record TransferCreateResponseDto(UUID transferId, String status) {
    public static TransferCreateResponseDto fromEntity(Transfer transfer) {
        return new TransferCreateResponseDto(transfer.getId(), transfer.getStatus().toString());
    }
}
