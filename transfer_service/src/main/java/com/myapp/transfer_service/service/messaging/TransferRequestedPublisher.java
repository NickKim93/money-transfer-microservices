package com.myapp.transfer_service.service.messaging;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.myapp.transfer_service.messaging.RabbitNames;
import com.myapp.transfer_service.messaging.dto.TransferRequestedMessage;
import com.myapp.transfer_service.model.Transfer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class TransferRequestedPublisher {
    
    private final RabbitTemplate rabbitTemplate;

    public void publishTransferRequested(Transfer transfer) {
        TransferRequestedMessage message = new TransferRequestedMessage(
            UUID.randomUUID(),
            transfer.getId(),
            transfer.getFromAccountId(),
            transfer.getToAccountId(),
            transfer.getAmount(),
            Instant.now()
        );

        rabbitTemplate.convertAndSend(
            RabbitNames.TRANSFER_EXCHANGE,
            RabbitNames.TRANSFER_REQUESTED_ROUTING_KEY,
            message
        );

        log.info("Transfer requested message sent: {}", message);
    }
}
