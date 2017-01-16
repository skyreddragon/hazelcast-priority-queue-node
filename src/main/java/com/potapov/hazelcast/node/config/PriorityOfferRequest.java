package com.potapov.hazelcast.node.config;

import com.hazelcast.nio.serialization.Data;
import com.hazelcast.queue.impl.client.OfferRequest;

public class PriorityOfferRequest extends OfferRequest {

    public PriorityOfferRequest() {
    }

    public PriorityOfferRequest(String name, Data data) {
        super(name, data);
    }

    public PriorityOfferRequest(String name, long timeoutMillis, Data data) {
        super(name, timeoutMillis, data);
    }

    public int getFactoryId() {
        return PriorityPortableHook.F_ID;
    }

    @Override
    public String getServiceName() {
        return PriorityQueueService.SERVICE_NAME;
    }
}
