package com.potapov.hazelcast.node.config;

import com.hazelcast.config.QueueConfig;
import com.hazelcast.config.QueueStoreConfig;
import com.hazelcast.core.HazelcastException;
import com.hazelcast.logging.ILogger;
import com.hazelcast.monitor.impl.LocalQueueStatsImpl;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.Data;
import com.hazelcast.nio.serialization.SerializationService;
import com.hazelcast.queue.impl.QueueContainer;
import com.hazelcast.queue.impl.QueueItem;
import com.hazelcast.queue.impl.QueueService;
import com.hazelcast.queue.impl.QueueStoreWrapper;
import com.hazelcast.queue.impl.QueueWaitNotifyKey;
import com.hazelcast.queue.impl.TxQueueItem;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.transaction.TransactionException;
import com.hazelcast.util.Clock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @ali 05/11/13
 */
public class PriorityQueueContainer extends QueueContainer {

    private FakeDequeWrapper<QueueItem> itemQueue = null;
    private HashMap<Long, QueueItem> backupMap = null;
    private final Map<Long, TxQueueItem> txMap = new HashMap<Long, TxQueueItem>();
    private final HashMap<Long, Data> dataMap = new HashMap<Long, Data>();

    private QueueConfig config;
    private QueueStoreWrapper store;
    private NodeEngine nodeEngine;
    private QueueService service;
    private ILogger logger;

    private long idGenerator = 0;

    private final QueueWaitNotifyKey pollWaitNotifyKey;
    private final QueueWaitNotifyKey offerWaitNotifyKey;

    private String name;

    private long minAge = Long.MAX_VALUE;

    private long maxAge = Long.MIN_VALUE;

    private long totalAge;

    private long totalAgedCount;

    private boolean isEvictionScheduled = false;

    public PriorityQueueContainer(String name) {
        super(name);
        this.name = name;
        pollWaitNotifyKey = new QueueWaitNotifyKey(name, "poll");
        offerWaitNotifyKey = new QueueWaitNotifyKey(name, "offer");
    }

    public PriorityQueueContainer(String name, QueueConfig config, NodeEngine nodeEngine, QueueService service) throws Exception {
        this(name);
        setConfig(config, nodeEngine, service);
    }

    public void init(boolean fromBackup) {
        if (!fromBackup && store.isEnabled()) {
            Set<Long> keys = store.loadAllKeys();
            if (keys != null) {
                for (Long key : keys) {
                    QueueItem item = new PriorityQueueItem(this, key, null);
                    getItemQueue().offer(item);
                    nextId();
                }
            }
        }
    }

    public <T> T toObject(Object object) {
        return nodeEngine.toObject(object);
    }

    //TX Methods

    public boolean txnEnsureReserve(long itemId) {
        if (txMap.get(itemId) == null) {
            throw new TransactionException("No reserve for itemId: " + itemId);
        }
        return true;
    }

    //TX Poll
    public QueueItem txnPollReserve(long reservedOfferId, String transactionId) {
        QueueItem item = getItemQueue().poll();
        if (item == null) {
            TxQueueItem txItem = txMap.remove(reservedOfferId);
            if (txItem == null) {
                return null;
            }
            item = new PriorityQueueItem(this, txItem.getItemId(), txItem.getData());
            return item;
        }
        if (store.isEnabled() && item.getData() == null) {
            try {
                load(item);
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        txMap.put(item.getItemId(), new TxQueueItem(item).setPollOperation(true).setTransactionId(transactionId));
        return item;
    }

    public void txnPollBackupReserve(long itemId, String transactionId) {
        QueueItem item = getBackupMap().remove(itemId);
        if (item == null) {
            throw new TransactionException("Backup reserve failed: " + itemId);
        }
        txMap.put(itemId, new TxQueueItem(item).setPollOperation(true).setTransactionId(transactionId));
    }

    public Data txnCommitPoll(long itemId) {
        final Data result = txnCommitPollBackup(itemId);
        scheduleEvictionIfEmpty();
        return result;
    }

    public Data txnCommitPollBackup(long itemId) {
        TxQueueItem item = txMap.remove(itemId);
        if (item == null) {
            logger.warning("txnCommitPoll operation-> No txn item for itemId: " + itemId);
            return null;
        }
        if (store.isEnabled()) {
            try {
                store.delete(item.getItemId());
            } catch (Exception e) {
                logger.severe("Error during store delete: " + item.getItemId(), e);
            }
        }
        return item.getData();
    }

    public boolean txnRollbackPoll(long itemId, boolean backup) {
        QueueItem item = txMap.remove(itemId);
        if (item == null) {
            logger.warning("No txn item for itemId: " + itemId);
            return false;
        }
        if (!backup) {
            getItemQueue().offer(item);
        }
        return true;
    }

    //TX Offer
    public long txnOfferReserve(String transactionId) {
        TxQueueItem item = new TxQueueItem(this, nextId(), null).setTransactionId(transactionId).setPollOperation(false);
        txMap.put(item.getItemId(), item);
        return item.getItemId();
    }

    public void txnOfferBackupReserve(long itemId, String transactionId) {
        QueueItem item = new PriorityQueueItem(this, itemId, null);
        Object o = txMap.put(itemId, new TxQueueItem(item).setPollOperation(false).setTransactionId(transactionId));
        if (o != null) {
            logger.severe("txnOfferBackupReserve operation-> Item exists already at txMap for itemId: " + itemId);
        }
    }

    public boolean txnCommitOffer(long itemId, Data data, boolean backup) {
        QueueItem item = txMap.remove(itemId);
        if (item == null && !backup) {
            throw new TransactionException("No reserve :" + itemId);
        } else if (item == null) {
            item = new PriorityQueueItem(this, itemId, data);
        }
        item.setData(data);
        if (!backup) {
            getItemQueue().offer(item);
        } else {
            getBackupMap().put(itemId, item);
        }
        if (store.isEnabled()) {
            try {
                store.store(item.getItemId(), data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public boolean txnRollbackOffer(long itemId) {
        final boolean result = txnRollbackOfferBackup(itemId);
        scheduleEvictionIfEmpty();
        return result;
    }

    public boolean txnRollbackOfferBackup(long itemId) {
        QueueItem item = txMap.remove(itemId);
        if (item == null) {
            logger.warning("txnRollbackOffer operation-> No txn item for itemId: " + itemId);
            return false;
        }
        return true;
    }

    public QueueItem txnPeek(long offerId, String transactionId) {
        QueueItem item = getItemQueue().peek();
        if (item == null) {
            if (offerId == -1) {
                return null;
            }
            TxQueueItem txItem = txMap.get(offerId);
            if (txItem == null) {
                return null;
            }
            item = new PriorityQueueItem(this, txItem.getItemId(), txItem.getData());
            return item;
        }
        if (store.isEnabled() && item.getData() == null) {
            try {
                load(item);
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        return item;
    }

    //TX Methods Ends


    public long offer(Data data) {
        QueueItem item = new PriorityQueueItem(this, nextId(), null);
        if (store.isEnabled()) {
            try {
                store.store(item.getItemId(), data);
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        if (!store.isEnabled() || store.getMemoryLimit() > getItemQueue().size()) {
            item.setData(data);
        }
        getItemQueue().offer(item);
        return item.getItemId();
    }

    public void offerBackup(Data data, long itemId) {
        QueueItem item = new PriorityQueueItem(this, itemId, null);
        if (!store.isEnabled() || store.getMemoryLimit() > getItemQueue().size()) {
            item.setData(data);
        }
        getBackupMap().put(itemId, item);
    }

    public Map<Long, Data> addAll(Collection<Data> dataList) {
        Map<Long, Data> map = new HashMap<Long, Data>(dataList.size());
        for (Data data : dataList) {
            QueueItem item = new PriorityQueueItem(this, nextId(), null);
            if (!store.isEnabled() || store.getMemoryLimit() > getItemQueue().size()) {
                item.setData(data);
            }
            getItemQueue().offer(item);
            map.put(item.getItemId(), data);
        }
        if (store.isEnabled()) {
            try {
                store.storeAll(map);
            } catch (Exception e) {
                for (int i = 0; i < dataList.size(); i++) {
                    getItemQueue().poll();
                }
                throw new HazelcastException(e);
            }
        }
        return map;
    }

    public void addAllBackup(Map<Long, Data> dataMap) {
        for (Map.Entry<Long, Data> entry : dataMap.entrySet()) {
            QueueItem item = new PriorityQueueItem(this, entry.getKey(), null);
            if (!store.isEnabled() || store.getMemoryLimit() > getItemQueue().size()) {
                item.setData(entry.getValue());
            }
            getBackupMap().put(item.getItemId(), item);
        }
    }

    public QueueItem peek() {
        QueueItem item = getItemQueue().peek();
        if (item == null) {
            return null;
        }
        if (store.isEnabled() && item.getData() == null) {
            try {
                load(item);
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        return item;
    }

    public QueueItem poll() {
        QueueItem item = peek();
        if (item == null) {
            return null;
        }
        if (store.isEnabled()) {
            try {
                store.delete(item.getItemId());
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        getItemQueue().poll();
        age(item, Clock.currentTimeMillis());
        scheduleEvictionIfEmpty();
        return item;
    }

    public void pollBackup(long itemId) {
        QueueItem item = getBackupMap().remove(itemId);
        if (item != null) {
            age(item, Clock.currentTimeMillis());//For Stats
        }
    }

    public Map<Long, Data> drain(int maxSize) {
        if (maxSize < 0 || maxSize > getItemQueue().size()) {
            maxSize = getItemQueue().size();
        }
        LinkedHashMap<Long, Data> map = new LinkedHashMap<Long, Data>(maxSize);
        Iterator<QueueItem> iter = getItemQueue().iterator();
        for (int i = 0; i < maxSize; i++) {
            QueueItem item = iter.next();
            if (store.isEnabled() && item.getData() == null) {
                try {
                    load(item);
                } catch (Exception e) {
                    throw new HazelcastException(e);
                }
            }
            map.put(item.getItemId(), item.getData());
        }
        if (store.isEnabled()) {
            try {
                store.deleteAll(map.keySet());
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        long current = Clock.currentTimeMillis();
        for (int i = 0; i < maxSize; i++) {
            QueueItem item = getItemQueue().poll();
            age(item, current); //For Stats
        }
        scheduleEvictionIfEmpty();
        return map;
    }

    public void drainFromBackup(Set<Long> itemIdSet) {
        for (Long itemId : itemIdSet) {
            pollBackup(itemId);
        }
        dataMap.clear();
    }

    public int size() {
        return Math.min(config.getMaxSize(), getItemQueue().size());
    }

    public int backupSize() {
        return getBackupMap().size();
    }

    public Map<Long, Data> clear() {
        long current = Clock.currentTimeMillis();
        LinkedHashMap<Long, Data> map = new LinkedHashMap<Long, Data>(getBackupMap().size());
        for (QueueItem item : getBackupMap().values()) {
            map.put(item.getItemId(), item.getData());
            age(item, current); // For stats
        }
        if (store.isEnabled()) {
            try {
                store.deleteAll(map.keySet());
            } catch (Exception e) {
                throw new HazelcastException(e);
            }
        }
        getItemQueue().clear();
        dataMap.clear();
        scheduleEvictionIfEmpty();
        return map;
    }

    public void clearBackup(Set<Long> itemIdSet) {
        drainFromBackup(itemIdSet);
    }

    /**
     * iterates all items, checks equality with data
     * This method does not trigger store load.
     */
    public long remove(Data data) {
        Iterator<QueueItem> iter = getItemQueue().iterator();
        while (iter.hasNext()) {
            QueueItem item = iter.next();
            if (data.equals(item.getData())) {
                if (store.isEnabled()) {
                    try {
                        store.delete(item.getItemId());
                    } catch (Exception e) {
                        throw new HazelcastException(e);
                    }
                }
                iter.remove();
                age(item, Clock.currentTimeMillis()); //For Stats
                scheduleEvictionIfEmpty();
                return item.getItemId();
            }
        }
        return -1;
    }

    public void removeBackup(long itemId) {
        getBackupMap().remove(itemId);
    }

    /**
     * This method does not trigger store load.
     */
    public boolean contains(Collection<Data> dataSet) {
        for (Data data : dataSet) {
            boolean contains = false;
            for (QueueItem item : getItemQueue()) {
                if (item.getData() != null && item.getData().equals(data)) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method triggers store load.
     */
    public List<Data> getAsDataList() {
        List<Data> dataList = new ArrayList<Data>(getItemQueue().size());
        for (QueueItem item : getItemQueue()) {
            if (store.isEnabled() && item.getData() == null) {
                try {
                    load(item);
                } catch (Exception e) {
                    throw new HazelcastException(e);
                }
            }
            dataList.add(item.getData());
        }
        return dataList;
    }

    /**
     * This method triggers store load
     */
    public Map<Long, Data> compareAndRemove(Collection<Data> dataList, boolean retain) {
        LinkedHashMap<Long, Data> map = new LinkedHashMap<Long, Data>();
        for (QueueItem item : getItemQueue()) {
            if (item.getData() == null && store.isEnabled()) {
                try {
                    load(item);
                } catch (Exception e) {
                    throw new HazelcastException(e);
                }
            }
            boolean contains = dataList.contains(item.getData());
            if ((retain && !contains) || (!retain && contains)) {
                map.put(item.getItemId(), item.getData());
            }
        }
        if (map.size() > 0) {
            if (store.isEnabled()) {
                try {
                    store.deleteAll(map.keySet());
                } catch (Exception e) {
                    throw new HazelcastException(e);
                }
            }
            Iterator<QueueItem> iter = getItemQueue().iterator();
            while (iter.hasNext()) {
                QueueItem item = iter.next();
                if (map.containsKey(item.getItemId())) {
                    iter.remove();
                    age(item, Clock.currentTimeMillis());//For Stats
                }
            }
            scheduleEvictionIfEmpty();
        }
        return map;
    }

    public void compareAndRemoveBackup(Set<Long> itemIdSet) {
        drainFromBackup(itemIdSet);
    }

    private void load(QueueItem item) throws Exception {
        int bulkLoad = store.getBulkLoad();
        bulkLoad = Math.min(getItemQueue().size(), bulkLoad);
        if (bulkLoad == 1) {
            item.setData(store.load(item.getItemId()));
        } else if (bulkLoad > 1) {
            Iterator<QueueItem> iter = getItemQueue().iterator();
            HashSet<Long> keySet = new HashSet<Long>(bulkLoad);
            for (int i = 0; i < bulkLoad; i++) {
                keySet.add(iter.next().getItemId());
            }
            Map<Long, Data> values = store.loadAll(keySet);
            dataMap.putAll(values);
            item.setData(getDataFromMap(item.getItemId()));
        }
    }

    public boolean hasEnoughCapacity() {
        return hasEnoughCapacity(1);
    }

    public boolean hasEnoughCapacity(int delta) {
        return (getItemQueue().size() + delta) <= config.getMaxSize();
    }

    public FakeDequeWrapper<QueueItem> getItemQueue() {
        if (itemQueue == null) {
            itemQueue = new FakeDequeWrapper<QueueItem>();
            if (backupMap != null && !backupMap.isEmpty()) {
                List<QueueItem> values = new ArrayList<QueueItem>(backupMap.values());
                Collections.sort(values);
                itemQueue.addAll(values);
                backupMap.clear();
                backupMap = null;
            }
        }
        return itemQueue;
    }

    Map<Long, QueueItem> getBackupMap() {
        if (backupMap == null) {
            backupMap = new HashMap<Long, QueueItem>();
            if (itemQueue != null) {
                for (QueueItem item : itemQueue) {
                    backupMap.put(item.getItemId(), item);
                }
                itemQueue.clear();
                itemQueue = null;
            }
        }
        return backupMap;
    }


    public Data getDataFromMap(long itemId) {
        return dataMap.remove(itemId);
    }

    public void setConfig(QueueConfig config, NodeEngine nodeEngine, QueueService service) {
        this.nodeEngine = nodeEngine;
        this.service = service;
        this.logger = nodeEngine.getLogger(QueueContainer.class);
        this.config = new QueueConfig(config);
        // init queue store.
        final QueueStoreConfig storeConfig = config.getQueueStoreConfig();
        final SerializationService serializationService = nodeEngine.getSerializationService();
        this.store = QueueStoreWrapper.create(name, storeConfig, serializationService);
    }

    long nextId() {
        return idGenerator++;
    }

    void setId(long itemId) {
        idGenerator = Math.max(itemId + 1, idGenerator);
    }

    public QueueWaitNotifyKey getPollWaitNotifyKey() {
        return pollWaitNotifyKey;
    }

    public QueueWaitNotifyKey getOfferWaitNotifyKey() {
        return offerWaitNotifyKey;
    }

    public QueueConfig getConfig() {
        return config;
    }

    private void age(QueueItem item, long currentTime) {
        long elapsed = currentTime - item.getCreationTime();
        if (elapsed <= 0) {
            return;//elapsed time can not be a negative value, a system clock problem maybe. ignored
        }
        totalAgedCount++;
        totalAge += elapsed;

        minAge = Math.min(minAge, elapsed);
        maxAge = Math.max(maxAge, elapsed);
    }

    public void setStats(LocalQueueStatsImpl stats) {
        stats.setMinAge(minAge);
        stats.setMaxAge(maxAge);
        long totalAgedCountVal = Math.max(totalAgedCount, 1);
        stats.setAveAge(totalAge / totalAgedCountVal);
    }

    private void scheduleEvictionIfEmpty() {
        final int emptyQueueTtl = config.getEmptyQueueTtl();
        if (emptyQueueTtl < 0) {
            return;
        }
        if (getItemQueue().isEmpty() && txMap.isEmpty() && !isEvictionScheduled) {
            if (emptyQueueTtl == 0) {
                nodeEngine.getProxyService().destroyDistributedObject(QueueService.SERVICE_NAME, name);
            } else if (emptyQueueTtl > 0) {
                service.scheduleEviction(name, emptyQueueTtl * 1000);
                isEvictionScheduled = true;
            }
        }
    }

    public void cancelEvictionIfExists() {
        if (isEvictionScheduled) {
            service.cancelEviction(name);
            isEvictionScheduled = false;
        }
    }

    public boolean isEvictable() {
        return getItemQueue().isEmpty() && txMap.isEmpty();
    }

    public void rollbackTransaction(String transactionId) {
        final Iterator<TxQueueItem> iterator = txMap.values().iterator();

        while (iterator.hasNext()) {
            final TxQueueItem item = iterator.next();
            if (transactionId.equals(item.getTransactionId())) {
                iterator.remove();
                if (item.isPollOperation()) {
                    getItemQueue().offer(item);
                }
            }
        }
    }

    public void writeData(ObjectDataOutput out) throws IOException {
        out.writeUTF(name);
        out.writeInt(getItemQueue().size());
        for (QueueItem item : getItemQueue()) {
            item.writeData(out);
        }
        out.writeInt(txMap.size());
        for (TxQueueItem item : txMap.values()) {
            item.writeData(out);
        }
    }

    public void readData(ObjectDataInput in) throws IOException {
        name = in.readUTF();
        int size = in.readInt();
        for (int j = 0; j < size; j++) {
            QueueItem item = new PriorityQueueItem(this, -1, null);
            item.readData(in);
            getItemQueue().offer(item);
            setId(item.getItemId());
        }
        int txSize = in.readInt();
        for (int j = 0; j < txSize; j++) {
            TxQueueItem item = new TxQueueItem(this, -1, null);
            item.readData(in);
            txMap.put(item.getItemId(), item);
            setId(item.getItemId());
        }
    }

    public void destroy() {
        if (itemQueue != null) {
            itemQueue.clear();
        }
        if (backupMap != null) {
            backupMap.clear();
        }
        txMap.clear();
        dataMap.clear();
    }

    public int getFactoryId() {
        return SerializerHook.F_ID;
    }

    public int getId() {
        return SerializerHook.CONTAINER;
    }
}