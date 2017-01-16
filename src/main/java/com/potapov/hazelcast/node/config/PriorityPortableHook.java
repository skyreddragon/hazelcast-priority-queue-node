package com.potapov.hazelcast.node.config;

import com.hazelcast.nio.serialization.ArrayPortableFactory;
import com.hazelcast.nio.serialization.ClassDefinition;
import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableFactory;
import com.hazelcast.nio.serialization.PortableHook;
import com.hazelcast.queue.impl.client.AddAllRequest;
import com.hazelcast.queue.impl.client.AddListenerRequest;
import com.hazelcast.queue.impl.client.ClearRequest;
import com.hazelcast.queue.impl.client.CompareAndRemoveRequest;
import com.hazelcast.queue.impl.client.ContainsRequest;
import com.hazelcast.queue.impl.client.DrainRequest;
import com.hazelcast.queue.impl.client.IteratorRequest;
import com.hazelcast.queue.impl.client.PeekRequest;
import com.hazelcast.queue.impl.client.RemainingCapacityRequest;
import com.hazelcast.queue.impl.client.RemoveRequest;
import com.hazelcast.queue.impl.client.SizeRequest;
import com.hazelcast.queue.impl.client.TxnOfferRequest;
import com.hazelcast.queue.impl.client.TxnPeekRequest;
import com.hazelcast.queue.impl.client.TxnPollRequest;
import com.hazelcast.queue.impl.client.TxnSizeRequest;
import com.hazelcast.util.ConstructorFunction;

import java.util.Collection;

public class PriorityPortableHook implements PortableHook {

    public static final int F_ID = 82;
    public static final int OFFER = 1;
    public static final int SIZE = 2;
    public static final int REMOVE = 3;
    public static final int POLL = 4;
    public static final int PEEK = 5;
    public static final int ITERATOR = 6;
    public static final int DRAIN = 7;
    public static final int CONTAINS = 8;
    public static final int COMPARE_AND_REMOVE = 9;
    public static final int CLEAR = 10;
    public static final int ADD_ALL = 11;
    public static final int ADD_LISTENER = 12;
    public static final int REMAINING_CAPACITY = 13;
    public static final int TXN_OFFER = 14;
    public static final int TXN_POLL = 15;
    public static final int TXN_SIZE = 16;
    public static final int TXN_PEEK = 17;

    public int getFactoryId() {
        return F_ID;
    }

    @Override
    public PortableFactory createFactory() {

        ConstructorFunction<Integer, Portable> constructors[] = new ConstructorFunction[TXN_PEEK + 1];

        constructors[OFFER] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new PriorityOfferRequest();
            }
        };
        constructors[SIZE] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new PrioritySizeRequest();
            }
        };
        constructors[REMOVE] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new RemoveRequest();
            }
        };
        constructors[POLL] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new PriorityPollRequest();
            }
        };
        constructors[PEEK] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new PeekRequest();
            }
        };
        constructors[ITERATOR] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new IteratorRequest();
            }
        };
        constructors[DRAIN] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new DrainRequest();
            }
        };
        constructors[CONTAINS] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new ContainsRequest();
            }
        };
        constructors[COMPARE_AND_REMOVE] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new CompareAndRemoveRequest();
            }
        };
        constructors[CLEAR] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new PriorityClearRequest();
            }
        };
        constructors[ADD_ALL] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new AddAllRequest();
            }
        };
        constructors[ADD_LISTENER] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new AddListenerRequest();
            }
        };
        constructors[REMAINING_CAPACITY] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new RemainingCapacityRequest();
            }
        };
        constructors[TXN_OFFER] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new TxnOfferRequest();
            }
        };
        constructors[TXN_POLL] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new TxnPollRequest();
            }
        };
        constructors[TXN_SIZE] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new TxnSizeRequest();
            }
        };
        constructors[TXN_PEEK] = new ConstructorFunction<Integer, Portable>() {
            @Override
            public Portable createNew(Integer arg) {
                return new TxnPeekRequest();
            }
        };

        return new ArrayPortableFactory(constructors);
    }

    public Collection<ClassDefinition> getBuiltinDefinitions() {
        return null;
    }
}
