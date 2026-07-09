package com.myapp.transfer_service.service.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.myapp.transfer_service.messaging.RabbitNames;
import com.myapp.transfer_service.messaging.dto.TransferResultMessage;
import com.myapp.transfer_service.service.TransferService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTransferResultListener {

    private final TransferService transferService;

    @RabbitListener(queues = RabbitNames.TRANSFER_RESULT_QUEUE)
    public void handleTransferResult(TransferResultMessage message) {
        log.info("Received transfer result message: {}", message);
        transferService.updateTransferStatus(message.transferId(), message.failureReason(), message.outcome());
    }
}
