package com.myapp.transfer_service.dto.response;

import java.util.UUID;

import com.myapp.transfer_service.model.Transfer;

public record TransferCreateResponseDto (
    UUID transferId,
    String status
) {
    public static TransferCreateResponseDto fromEntity(Transfer transfer) {
        return new TransferCreateResponseDto(
            transfer.getId(),
            transfer.getStatus().toString()
        );
    }
}
