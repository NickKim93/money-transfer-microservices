package com.myapp.transfer_service.messaging;

import com.myapp.transfer_service.messaging.dto.TransferResultMessage;
import com.myapp.transfer_service.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTransferResultListener {
    private final TransferService transferService;

    @RabbitListener(queues = RabbitNames.TRANSFER_RESULT_QUEUE)
    public void handleTransferResult(TransferResultMessage message) {
        try (MDC.MDCCloseable transfer =
                        MDC.putCloseable("transferId", String.valueOf(message.transferId()));
                MDC.MDCCloseable event =
                        MDC.putCloseable("eventId", String.valueOf(message.eventId()))) {
            log.info("Processing transfer result outcome={}", message.outcome());
            transferService.updateTransferStatus(
                    message.transferId(), message.failureReason(), message.outcome());
        }
    }
}
