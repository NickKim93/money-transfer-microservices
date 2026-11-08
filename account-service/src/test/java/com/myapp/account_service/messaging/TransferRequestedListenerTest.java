package com.myapp.account_service.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.myapp.account_service.exception.DuplicateTransferMessageException;
import com.myapp.account_service.exception.InsufficientFundsException;
import com.myapp.account_service.exception.InvalidAccountOperationException;
import com.myapp.account_service.messaging.dto.TransferRequestedMessage;
import com.myapp.account_service.messaging.dto.TransferResultMessage;
import com.myapp.account_service.messaging.status.TransferOutcome;
import com.myapp.account_service.service.AccountService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.dao.DataAccessResourceFailureException;

class TransferRequestedListenerTest {
    private final AccountService service = mock(AccountService.class);
    private final AccountTransferResultPublisher publisher =
            mock(AccountTransferResultPublisher.class);
    private final TransferRequestedListener listener =
            new TransferRequestedListener(service, publisher);
    private final TransferRequestedMessage message =
            new TransferRequestedMessage(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.TEN,
                    Instant.now());

    @Test
    void duplicatePublishesCompleted() {
        failTransfer(new DuplicateTransferMessageException(message.transferId().toString()));
        listener.handleTransferRequested(message);
        assertOutcome(TransferOutcome.COMPLETED);
    }

    @Test
    void businessRejectionPublishesFailed() {
        failTransfer(new InsufficientFundsException(message.fromAccountId()));
        listener.handleTransferRequested(message);
        assertOutcome(TransferOutcome.FAILED);
    }

    @Test
    void invalidBusinessOperationPublishesFailed() {
        failTransfer(new InvalidAccountOperationException("Account currencies must match"));
        listener.handleTransferRequested(message);
        assertOutcome(TransferOutcome.FAILED);
    }

    @Test
    void programmingErrorIsNotReportedAsBusinessRejection() {
        failTransfer(new IllegalArgumentException("Unexpected internal configuration"));
        assertThatThrownBy(() -> listener.handleTransferRequested(message))
                .isInstanceOf(IllegalArgumentException.class);
        verify(publisher, never()).publishTransferResult(any());
    }

    @Test
    void infrastructureFailurePropagatesWithoutPublishingFailed() {
        failTransfer(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> listener.handleTransferRequested(message))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(publisher, never()).publishTransferResult(any());
        assertThat(MDC.get("transferId")).isNull();
        assertThat(MDC.get("eventId")).isNull();
    }

    @Test
    void publicationFailureIsNotConvertedToFailedResult() {
        doThrow(new AmqpException("broker unavailable"))
                .when(publisher)
                .publishTransferResult(any());
        assertThatThrownBy(() -> listener.handleTransferRequested(message))
                .isInstanceOf(AmqpException.class);
        verify(publisher).publishTransferResult(any());
        assertThat(MDC.get("transferId")).isNull();
    }

    private void failTransfer(RuntimeException failure) {
        when(service.transfer(
                        message.transferId(),
                        message.fromAccountId(),
                        message.toAccountId(),
                        message.amount()))
                .thenThrow(failure);
    }

    private void assertOutcome(TransferOutcome outcome) {
        ArgumentCaptor<TransferResultMessage> result =
                ArgumentCaptor.forClass(TransferResultMessage.class);
        verify(publisher).publishTransferResult(result.capture());
        assertThat(result.getValue().outcome()).isEqualTo(outcome);
        assertThat(result.getValue().transferId()).isEqualTo(message.transferId());
    }
}
