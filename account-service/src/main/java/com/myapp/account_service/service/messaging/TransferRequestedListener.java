package com.myapp.account_service.service.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.myapp.account_service.exception.AccountNotFoundException;
import com.myapp.account_service.exception.InsufficientFundsException;
import com.myapp.account_service.messaging.RabbitNames;
import com.myapp.account_service.messaging.dto.TransferRequestedMessage;
import com.myapp.account_service.messaging.dto.TransferResultMessage;
import com.myapp.account_service.messaging.status.TransferOutcome;
import com.myapp.account_service.service.AccountService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferRequestedListener {

    private final AccountService accountService;

    private final AccountTransferResultPublisher transferResultPublisher;

    @RabbitListener(queues = RabbitNames.ACCOUNT_TRANSFER_REQUESTED_QUEUE)
    public void handleTransferRequested(TransferRequestedMessage message) {
        log.info("Received transfer requested message: {}", message);
        try {
            accountService.transfer(message.fromAccountId(), message.toAccountId(), message.amount());
            transferResultPublisher.publishTransferResult(
                new TransferResultMessage(UUID.randomUUID(),message.transferId(), TransferOutcome.COMPLETED, null, Instant.now())
            );
            log.info("Transfer completed successfully.");
        } catch (AccountNotFoundException e) {
            log.error("Account not found: {}", e.getMessage());
            transferResultPublisher.publishTransferResult(
                new TransferResultMessage(UUID.randomUUID(),message.transferId(), TransferOutcome.FAILED, e.getMessage(), Instant.now())
            );
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds: {}", e.getMessage());
            transferResultPublisher.publishTransferResult(
                new TransferResultMessage(UUID.randomUUID(),message.transferId(), TransferOutcome.FAILED, e.getMessage(), Instant.now())
            );
        } catch (Exception e) {
            log.error("Error processing transfer: {}", e.getMessage());
            transferResultPublisher.publishTransferResult(
                new TransferResultMessage(UUID.randomUUID(),message.transferId(), TransferOutcome.FAILED, e.getMessage(), Instant.now())
            );
        }
    }
}
