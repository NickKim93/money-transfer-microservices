package com.myapp.transfer_service.messaging;

public final class KafkaNames {

    private KafkaNames() {}

    public static final String TRANSFER_REQUESTED_TOPIC = "transfer.requested.v1";
    public static final int TRANSFER_REQUESTED_PARTITIONS = 3;
    public static final String ACCOUNT_SERVICE_GROUP = "account-service";
}
