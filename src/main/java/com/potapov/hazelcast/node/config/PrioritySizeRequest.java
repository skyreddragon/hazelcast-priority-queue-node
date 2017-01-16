package com.potapov.hazelcast.node.config;

import com.hazelcast.queue.impl.client.SizeRequest;

/**
 * Created by днс on 16.01.2017.
 */
public class PrioritySizeRequest extends SizeRequest {

    public PrioritySizeRequest() {
    }

    public PrioritySizeRequest(String name) {
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
