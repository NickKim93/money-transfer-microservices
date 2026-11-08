package com.myapp.transfer_service.service;

import com.myapp.transfer_service.dto.request.TransferRequestDto;
import com.myapp.transfer_service.exception.TransferNotFoundException;
import com.myapp.transfer_service.model.Status;
import com.myapp.transfer_service.model.Transfer;
import com.myapp.transfer_service.repository.TransferRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Переходы статуса перевода. Result-события приходят at-least-once, поэтому переходы защищены
 * гвардом: из терминального статуса не уходим, повтор в тот же статус — no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferStatusService {

    private final TransferRepository transferRepository;

    @Transactional
    public Transfer createPending(TransferRequestDto request) {
        Transfer transfer =
                Transfer.builder()
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
        if (!canTransition(transfer, Status.COMPLETED)) {
            return transfer;
        }
        transfer.setStatus(Status.COMPLETED);
        transfer.setFailureReason(null);
        return transferRepository.save(transfer);
    }

    @Transactional
    public Transfer markFailed(UUID transferId, String failReason) {
        Transfer transfer = getTransferOrThrow(transferId);
        if (!canTransition(transfer, Status.FAILED)) {
            return transfer;
        }
        transfer.setStatus(Status.FAILED);
        transfer.setFailureReason(failReason);
        return transferRepository.save(transfer);
    }

    private Transfer getTransferOrThrow(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));
    }

    /**
     * Повтор в тот же терминальный статус — штатный дубль (info). Попытка уйти из одного
     * терминального статуса в другой — аномалия (warn), но не исключение: иначе листенер уйдёт в
     * бесконечный redelivery.
     */
    private boolean canTransition(Transfer transfer, Status target) {
        Status current = transfer.getStatus();
        if (!current.isTerminal()) {
            return true;
        }
        if (current == target) {
            log.info("Transfer {} already {}, no-op", transfer.getId(), current);
        } else {
            log.warn(
                    "Transfer {} is {} (terminal), ignoring transition to {}",
                    transfer.getId(),
                    current,
                    target);
        }
        return false;
    }
}
