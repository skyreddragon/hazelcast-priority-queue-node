package com.potapov.hazelcast.node.config;

import com.hazelcast.queue.impl.client.PollRequest;

/**
 * Created by днс on 15.01.2017.
 */
public class PriorityPollRequest extends PollRequest {

    public PriorityPollRequest() {
    }

    public PriorityPollRequest(String name) {
        super(name);
    }

    public PriorityPollRequest(String name, long timeoutMillis) {
        super(name, timeoutMillis);
    }

    @Override
    public int getFactoryId() {
        return PriorityPortableHook.F_ID;
    }

    @Override
    public String getServiceName() {
        return PriorityQueueService.SERVICE_NAME;
    }
}
