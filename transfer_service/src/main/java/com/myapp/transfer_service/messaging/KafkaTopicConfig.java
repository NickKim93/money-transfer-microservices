package com.myapp.transfer_service.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Топики, владельцем которых является transfer-service (владелец события = продьюсер). Брокер
 * запускается с auto.create.topics.enable=false, поэтому топик создаёт KafkaAdmin при старте.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic transferRequestedTopic() {
        return TopicBuilder.name(KafkaNames.TRANSFER_REQUESTED_TOPIC)
                .partitions(KafkaNames.TRANSFER_REQUESTED_PARTITIONS)
                .replicas(1)
                .build();
    }
}
