package com.kmfrog.martlet.book;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.kmfrog.martlet.book.RollingTradeLog.Timestamp;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * 一个指定窗口时间数据容器。
 * 
 * @author dust Oct 31, 2019
 *
 */
public class RollingTradeLog<T extends Timestamp> {

    private final long windowMillis;
    private final Long2ObjectSortedMap<T> rolling;
    private final ReentrantReadWriteLock lock;

    public RollingTradeLog(long windowMillis, T t) {
        this.windowMillis = windowMillis;
        rolling = new Long2ObjectLinkedOpenHashMap<>();
        lock = new ReentrantReadWriteLock();
    }

    public long avg() {
        drainout();
        lock.readLock().lock();
        try {
            // queue.
        } finally {
            lock.readLock().unlock();
        }
        return 0L;
    }

    public void drainout() {
        lock.writeLock().lock();
        try {
            Long2ObjectSortedMap<T> maps = rolling.headMap(System.currentTimeMillis() - windowMillis);
            if (!maps.isEmpty()) {
                LongSortedSet set = maps.keySet();
                set.stream().forEach(k -> rolling.remove(k));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static interface Timestamp {
        long getTimestamp();

        long getPrice();
    }

}
