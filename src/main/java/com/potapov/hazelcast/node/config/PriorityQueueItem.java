package com.potapov.hazelcast.node.config;

import com.hazelcast.nio.serialization.Data;
import com.hazelcast.queue.impl.QueueItem;

/**
 * @ali 05/11/13
 */
public class PriorityQueueItem extends QueueItem {


    protected Object object;

    public PriorityQueueItem() {
        super();
    }

    public PriorityQueueItem(PriorityQueueContainer container, long itemId, Data data) {
        super(container, itemId, data);
        if (container != null){
            object = container.toObject(data);
        }
    }

    public int compareTo(QueueItem o) {
        if (o instanceof PriorityQueueItem && object != null && object instanceof Comparable){
            return ((Comparable) object).compareTo(((PriorityQueueItem) o).object);
        }
        return super.compareTo(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriorityQueueItem)) return false;

        final PriorityQueueItem priorityQueueItem = (PriorityQueueItem) o;
        if (object != null){
            return object.equals(priorityQueueItem.object);
        } else {
            return data.equals(data);
        }
    }

    public void setData(Data data) {
        if (container != null){
            object = ((PriorityQueueContainer)container).toObject(data);
        }
        super.setData(data);
    }
}
