package com.myapp.account_service.messaging;

import com.myapp.account_service.exception.AccountNotFoundException;
import com.myapp.account_service.exception.DuplicateTransferMessageException;
import com.myapp.account_service.exception.InsufficientFundsException;
import com.myapp.account_service.exception.InvalidAccountOperationException;
import com.myapp.account_service.messaging.dto.TransferRequestedMessage;
import com.myapp.account_service.messaging.dto.TransferResultMessage;
import com.myapp.account_service.messaging.status.TransferOutcome;
import com.myapp.account_service.service.AccountService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransferRequestedListener {
    private final AccountService accountService;
    private final AccountTransferResultPublisher transferResultPublisher;

    @KafkaListener(
            topics = KafkaNames.TRANSFER_REQUESTED_TOPIC,
            groupId = KafkaNames.ACCOUNT_SERVICE_GROUP)
    public void handleTransferRequested(TransferRequestedMessage message) {
        try (MDC.MDCCloseable transfer =
                        MDC.putCloseable("transferId", String.valueOf(message.transferId()));
                MDC.MDCCloseable event =
                        MDC.putCloseable("eventId", String.valueOf(message.eventId()))) {
            log.info("Processing transfer request");
            TransferResultMessage result = process(message);
            // Publication errors must propagate so the container does not acknowledge a lost
            // result.
            transferResultPublisher.publishTransferResult(result);
            log.info("Published transfer result outcome={}", result.outcome());
        }
    }

    private TransferResultMessage process(TransferRequestedMessage message) {
        try {
            accountService.transfer(
                    message.transferId(),
                    message.fromAccountId(),
                    message.toAccountId(),
                    message.amount());
            return result(message.transferId(), TransferOutcome.COMPLETED, null);
        } catch (DuplicateTransferMessageException ex) {
            log.info("Transfer already committed; publishing COMPLETED again");
            return result(message.transferId(), TransferOutcome.COMPLETED, null);
        } catch (AccountNotFoundException
                | InsufficientFundsException
                | InvalidAccountOperationException ex) {
            log.warn("Transfer rejected reason={}", ex.getMessage());
            return result(message.transferId(), TransferOutcome.FAILED, ex.getMessage());
        }
        // Infrastructure failures propagate; only business rejections are terminal FAILED results.
    }

    private TransferResultMessage result(UUID transferId, TransferOutcome outcome, String reason) {
        return new TransferResultMessage(
                UUID.randomUUID(), transferId, outcome, reason, Instant.now());
    }
}
