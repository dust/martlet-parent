package com.kmfrog.martlet.book;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;

import com.kmfrog.martlet.book.RollingTimeSpan.Timestamp;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * 一个指定窗口时间数据容器。
 * 
 * @author dust Oct 31, 2019
 *
 */
public class RollingTimeSpan<T extends Timestamp> {

    private final long windowMillis;
    private final Long2ObjectSortedMap<T> rolling;
    private final ReentrantReadWriteLock lock;

    public RollingTimeSpan(long windowMillis, T t) {
        this.windowMillis = windowMillis;
        rolling = new Long2ObjectLinkedOpenHashMap<>();
        lock = new ReentrantReadWriteLock();
    }

    public void add(T t) {
        lock.writeLock().lock();
        try {
            rolling.put(t.getTimestamp(), t);
            drainout();  //重进入。
        } finally {
            lock.writeLock().unlock();
        }

    }

    public long avg() {
        drainout();
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            LongStream longs = rolling.values().stream().mapToLong(v -> v.getPrice());
            return longs.sum() / longs.count();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long latest() {
        drainout();
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            return rolling.lastLongKey();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long last() {
        drainout();
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            return rolling.firstLongKey();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void drainout() {
        lock.writeLock().lock();
        try {
            Long2ObjectSortedMap<T> maps = rolling.headMap(System.currentTimeMillis() - windowMillis);
            if (!maps.isEmpty()) {
                LongSortedSet set = maps.keySet();
                set.stream().forEach(k -> rolling.remove(k.longValue()));
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
