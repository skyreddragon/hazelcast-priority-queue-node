package com.potapov.hazelcast.node.config;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.DataSerializerHook;
import com.hazelcast.nio.serialization.FactoryIdHelper;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;

/**
 * @ali 05/11/13
 */

public class SerializerHook implements DataSerializerHook {

    static final int F_ID = FactoryIdHelper.getFactoryId("hazelcast.serialization.priority.queue", -81);

    static final int CONTAINER = 1;

    public int getFactoryId() {
        return F_ID;
    }

    public DataSerializableFactory createFactory() {
        return new DataSerializableFactory() {
            public IdentifiedDataSerializable create(int typeId) {
                switch (typeId){
                    case CONTAINER:
                        return new PriorityQueueContainer(null);
                }
                return null;
            }
        };
    }
}
