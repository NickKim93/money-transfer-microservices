package com.myapp.account_service.service.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.myapp.account_service.messaging.RabbitNames;
import com.myapp.account_service.messaging.dto.TransferResultMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountTransferResultPublisher {
    
    private final RabbitTemplate rabbitTemplate;

    public void publishTransferResult(TransferResultMessage message) {
        rabbitTemplate.convertAndSend(
            RabbitNames.TRANSFER_EXCHANGE,
            RabbitNames.TRANSFER_RESULT_ROUTING_KEY,
            message);
    }
}
