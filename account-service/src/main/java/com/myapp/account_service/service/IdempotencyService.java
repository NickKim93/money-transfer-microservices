package com.myapp.account_service.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.myapp.account_service.exception.DuplicateTransferMessageException;
import com.myapp.account_service.model.ProcessedTransfer;
import com.myapp.account_service.repository.ProcessedTransferRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedTransferRepository processedTransferRepository;

    /**
     * Помечает transferId как обработанный. Вызывается ВНУТРИ транзакции движения
     * денег (не открывает свою), чтобы отметка и эффект коммитились атомарно.
     * Повторный transferId нарушает PK -> DuplicateTransferMessageException.
     */
    public void markProcessed(UUID transferId) {
        try {
            processedTransferRepository.saveAndFlush(new ProcessedTransfer(transferId, Instant.now()));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateTransferMessageException(transferId.toString());
        }
    }
}
