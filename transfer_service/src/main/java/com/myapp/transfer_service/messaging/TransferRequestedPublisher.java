package com.myapp.transfer_service.messaging;

import com.myapp.transfer_service.messaging.dto.TransferRequestedMessage;
import com.myapp.transfer_service.model.Transfer;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferRequestedPublisher {

    private final KafkaTemplate<String, TransferRequestedMessage> kafkaTemplate;

    public void publishTransferRequested(Transfer transfer) {
        TransferRequestedMessage message =
                new TransferRequestedMessage(
                        UUID.randomUUID(),
                        transfer.getId(),
                        transfer.getFromAccountId(),
                        transfer.getToAccountId(),
                        transfer.getAmount(),
                        Instant.now());

        // Ключ = fromAccountId: все списания с одного счёта попадают в одну партицию и
        // обрабатываются по порядку (обоснование — README, «Kafka design»).
        String key = message.fromAccountId().toString();

        // known gap: dual write, see outbox — Transfer=PENDING уже закоммичен, а send() может не
        // дойти до брокера (перевод зависнет в PENDING). Закрывается outbox'ом (W3 Day 4–5).
        kafkaTemplate
                .send(KafkaNames.TRANSFER_REQUESTED_TOPIC, key, message)
                .whenComplete(
                        (result, ex) -> {
                            if (ex != null) {
                                log.error(
                                        "Failed to publish TransferRequested transferId={}",
                                        message.transferId(),
                                        ex);
                            } else {
                                var metadata = result.getRecordMetadata();
                                log.debug(
                                        "TransferRequested sent transferId={} partition={}"
                                                + " offset={}",
                                        message.transferId(),
                                        metadata.partition(),
                                        metadata.offset());
                            }
                        });
    }
}
