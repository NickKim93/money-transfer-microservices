package com.myapp.account_service.messaging;

public final class RabbitNames {

    private RabbitNames() {}

    public static final String TRANSFER_EXCHANGE = "transfer.exchange";
    public static final String TRANSFER_REQUESTED_ROUTING_KEY = "transfer.requested";
    public static final String ACCOUNT_TRANSFER_REQUESTED_QUEUE = "account.transfer.requested.queue";
    public static final String TRANSFER_RESULT_ROUTING_KEY = "transfer.result";
    public static final String TRANSFER_RESULT_QUEUE = "transfer.result.queue";
}
