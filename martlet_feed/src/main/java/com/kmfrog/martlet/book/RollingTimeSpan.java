package com.kmfrog.martlet.book;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.LongStream;

import com.kmfrog.martlet.feed.domain.InstrumentTimeSpan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * 一个指定窗口时间数据容器。
 * 
 * @author dust Oct 31, 2019
 *
 */
public class RollingTimeSpan<T extends InstrumentTimeSpan> {

    private final long windowMillis;
    private final ObjectList<T> rolling;
    private final ReentrantReadWriteLock lock;

    public RollingTimeSpan(long windowMillis) {
        this.windowMillis = windowMillis;
        rolling = new ObjectArrayList<>();
        lock = new ReentrantReadWriteLock();
    }

    public void add(T t) {
        lock.writeLock().lock();
        try {
            rolling.add(t);
            drainout(); // 重进入。
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
            LongStream longs = rolling.stream().mapToLong(v -> v.getPrice());
            return longs.sum() / rolling.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long sum() {
        drainout();
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            LongStream longs = rolling.stream().mapToLong(v -> v.getInstrument());
            return longs.sum();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long last() {
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            return rolling.get(rolling.size() - 1).getTimestamp();
        } finally {
            lock.readLock().unlock();
        }
    }

    public long first() {
        lock.readLock().lock();
        try {
            if (rolling.isEmpty()) {
                return 0L;
            }
            return rolling.get(0).getTimestamp();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void drainout() {
        lock.writeLock().lock();
        try {
            long last = last();
            while (last - first() > windowMillis) {
                rolling.remove(0);
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

}
