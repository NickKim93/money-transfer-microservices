package com.myapp.transfer_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTransferConfig {
    
    @Bean
    public TopicExchange transferExchange() {
        return new TopicExchange(RabbitNames.TRANSFER_EXCHANGE);
    }

    @Bean
    public Queue transferResultQueue() {
        return QueueBuilder.durable(RabbitNames.TRANSFER_RESULT_QUEUE).build();
    }

    @Bean
    public Binding transferResultBinding() {
        return BindingBuilder
            .bind(transferResultQueue())
            .to(transferExchange())
            .with(RabbitNames.TRANSFER_RESULT_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
