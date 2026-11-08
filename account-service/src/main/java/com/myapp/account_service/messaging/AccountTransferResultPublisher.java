package com.myapp.account_service.messaging;

import com.myapp.account_service.messaging.dto.TransferResultMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountTransferResultPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishTransferResult(TransferResultMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitNames.TRANSFER_EXCHANGE, RabbitNames.TRANSFER_RESULT_ROUTING_KEY, message);
    }
}
