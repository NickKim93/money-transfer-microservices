package com.myapp.account_service.messaging;

public final class RabbitNames {

    private RabbitNames() {}

    public static final String TRANSFER_EXCHANGE = "transfer.exchange";
    public static final String TRANSFER_RESULT_ROUTING_KEY = "transfer.result";
    public static final String TRANSFER_RESULT_QUEUE = "transfer.result.queue";
}
