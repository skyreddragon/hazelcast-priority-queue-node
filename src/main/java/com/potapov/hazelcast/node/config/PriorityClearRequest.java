package com.potapov.hazelcast.node.config;

import com.hazelcast.queue.impl.client.ClearRequest;

/**
 * Created by днс on 16.01.2017.
 */
public class PriorityClearRequest extends ClearRequest {

    public PriorityClearRequest() {
    }

    public PriorityClearRequest(String name) {
        super(name);
    }

    @Override
    public String getServiceName() {
        return PriorityQueueService.SERVICE_NAME;
    }

    @Override
    public int getFactoryId() {
        return PriorityPortableHook.F_ID;
    }
}
