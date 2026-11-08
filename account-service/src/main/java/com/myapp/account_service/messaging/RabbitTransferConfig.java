package com.myapp.account_service.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * На RabbitMQ остался только result-путь (account-service → transfer-service). Requested-путь
 * переехал на Kafka (W3 Day 1), result-путь переедет на W3 Day 2.
 */
@Configuration
public class RabbitTransferConfig {

    @Bean
    public TopicExchange transferExchange() {
        return new TopicExchange(RabbitNames.TRANSFER_EXCHANGE);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
