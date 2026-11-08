package com.myapp.account_service.service;

import com.myapp.account_service.exception.DuplicateTransferMessageException;
import com.myapp.account_service.repository.ProcessedTransferRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final ProcessedTransferRepository processedTransferRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(UUID transferId) {
        if (processedTransferRepository.insertIfAbsent(transferId, Instant.now()) == 0) {
            throw new DuplicateTransferMessageException(transferId.toString());
        }
    }
}
