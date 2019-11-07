package com.kmfrog.martlet.book;

import static com.kmfrog.martlet.C.SEPARATOR;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.util.Fmt;

import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2LongRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

/**
 * An basic order book. 一个线程安全的基本订单簿。
 */
public class OrderBook implements IOrderBook {

    private final Source src;
    private final long instrument;
    private final AtomicLong lastUpdate;
    private final AtomicLong lastReceived;

    private final Long2LongRBTreeMap bids;
    private final Long2LongRBTreeMap asks;

    private final ReadWriteLock bidLock;
    private final ReadWriteLock askLock;

    public OrderBook(Source src, long instrument) {
        this.instrument = instrument;
        this.src = src;
        lastReceived = new AtomicLong(0L);
        lastUpdate = new AtomicLong(0L);

        bids = new Long2LongRBTreeMap(LongComparators.OPPOSITE_COMPARATOR);
        asks = new Long2LongRBTreeMap(LongComparators.NATURAL_COMPARATOR);

        bidLock = new ReentrantReadWriteLock();
        askLock = new ReentrantReadWriteLock();

    }

    @Override
    public void destroy() {
        bidLock.writeLock().lock();
        try {
            bids.clear();
        } finally {
            bidLock.writeLock().unlock();
        }
        askLock.writeLock().lock();
        try {
            asks.clear();
        } finally {
            askLock.writeLock().unlock();
        }
    }

    /**
     * Get the instrument.
     *
     * @return the instrument
     */
    @Override
    public long getInstrument() {
        return instrument;
    }

    @Override
    public long getSourceValue() {
        return src.ordinal();
    }

    /**
     * Get the best bid price.
     *
     * @return the best bid price or zero if there are no bids
     */
    @Override
    public long getBestBidPrice() {
        bidLock.readLock().lock();
        try {
            if (bids.isEmpty()) {
                return 0;
            }

            return bids.firstLongKey();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Get the best bid price.
     *
     * @return the best bid price or zero if there are no bids
     */
    @Override
    public long getWorstBidPrice() {
        bidLock.readLock().lock();
        try {
            if (bids.isEmpty()) {
                return 0;
            }

            return bids.lastLongKey();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Get the bid prices.
     *
     * @return the bid prices
     */
    @Override
    public LongSortedSet getBidPrices() {
        bidLock.readLock().lock();
        try {
            return bids.keySet();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Get a bid level size.
     *
     * @param price the bid price
     * @return the bid level size
     */
    @Override
    public long getBidSize(long price) {
        bidLock.readLock().lock();
        try {
            return bids.get(price);
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Get the best ask price.
     *
     * @return the best ask price or zero if there are no asks
     */
    @Override
    public long getBestAskPrice() {
        askLock.readLock().lock();
        try {
            if (asks.isEmpty()) {
                return 0;
            }
            return asks.firstLongKey();
        } finally {
            askLock.readLock().unlock();
        }
    }

    @Override
    public long getWorstAskPrice() {
        askLock.readLock().lock();
        try {
            if (asks.isEmpty()) {
                return 0;
            }
            return asks.lastLongKey();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Get the ask prices.
     *
     * @return the ask prices
     */
    @Override
    public LongSortedSet getAskPrices() {
        askLock.readLock().lock();
        try {
            return asks.keySet();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Get an ask level size.
     *
     * @param price the ask price
     * @return the ask level size
     */
    @Override
    public long getAskSize(long price) {
        askLock.readLock().lock();
        try {
            return asks.get(price);
        } finally {
            askLock.readLock().unlock();
        }
    }

    @Override
    public long getLastUpdateTs() {
        return lastUpdate.get();
    }

    @Override
    public long getLastReceivedTs() {
        return lastReceived.get();
    }

    @Override
    public void setLastUpdateTs(long ts) {
        lastReceived.set(System.currentTimeMillis());
        lastUpdate.set(ts);
    }

    @Override
    public boolean replace(Side side, long price, long quantity, int source) {
        // bids or asks
        Long2LongRBTreeMap levels = getLevels(side);
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            if (quantity > 0) {
                levels.put(price, quantity);
            } else {
                levels.remove(price);
            }
            return (levels.size() > 0 && price == levels.firstLongKey()) || (levels.size() == 0 && quantity == 0);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean incr(Side side, long price, long quantity, int source) {
        // bids or asks
        Long2LongRBTreeMap levels = getLevels(side);
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            long oldSize = levels.get(price);
            long newSize = oldSize + quantity;

            boolean onBestLevel = price == levels.firstLongKey();

            if (newSize > 0) {
                levels.put(price, newSize);
            } else {
                levels.remove(price);
            }

            return onBestLevel;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean clear(Side side, int source) {
        Long2LongRBTreeMap levels = getLevels(side);
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            levels.clear();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getPlainText(Source src, int pricePrecision, int volumePrecision, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(src.ordinal()).append(SEPARATOR).append(instrument).append(SEPARATOR);
        sb.append(lastUpdate).append(SEPARATOR).append(lastReceived).append(SEPARATOR);
        sb.append('[');
        sb.append(dumpPlainText(Side.BUY, pricePrecision, volumePrecision, maxLevel));
        sb.append(']').append(SEPARATOR).append('[');
        sb.append(dumpPlainText(Side.SELL, pricePrecision, volumePrecision, maxLevel));
        sb.append(']').append(']');
        return sb.toString();
    }

    @Override
    public String getOriginText(Source src, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(src.ordinal()).append(SEPARATOR).append(instrument).append(SEPARATOR);
        sb.append(lastUpdate).append(SEPARATOR).append(lastReceived).append(SEPARATOR);
        sb.append('[');
        sb.append(dumpOriginText(Side.BUY, maxLevel));
        sb.append(']').append(SEPARATOR).append('[');
        sb.append(dumpOriginText(Side.SELL, maxLevel));
        sb.append(']').append(']');
        return sb.toString();
    }

    @Override
    public String dumpPlainText(Side side, int pricePrecision, int volumePrecision, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        Lock lock = side == Side.BUY ? bidLock.readLock() : askLock.readLock();

        lock.lock();
        try {
            Long2LongRBTreeMap levels = getLevels(side);
            int index = 0;
            for (ObjectBidirectionalIterator<Entry> iter = levels.long2LongEntrySet().iterator(); iter.hasNext()
                    && index < maxLevel;) {
                Entry entry = iter.next();
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append('[').append(Fmt.fmtNum(entry.getLongKey(), pricePrecision)).append(SEPARATOR)
                        .append(Fmt.fmtNum(entry.getLongValue(), volumePrecision)).append(']');
                index++;
            }
        } finally {
            lock.unlock();
        }
        return sb.toString();
    }

    @Override
    public String dumpOriginText(Side side, int maxLevel) {
        StringBuilder sb = new StringBuilder();
        Lock lock = side == Side.BUY ? bidLock.readLock() : askLock.readLock();

        lock.lock();
        try {
            Long2LongRBTreeMap levels = getLevels(side);
            int index = 0;
            for (ObjectBidirectionalIterator<Entry> iter = levels.long2LongEntrySet().iterator(); iter.hasNext()
                    && index < maxLevel;) {
                Entry entry = iter.next();
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append('[').append(entry.getLongKey()).append(SEPARATOR).append(entry.getLongValue()).append(']');
                index++;
            }
        } finally {
            lock.unlock();
        }
        return sb.toString();
    }

    @Override
    public void dump(Side side, PrintStream writer) {
        Lock lock = side == Side.BUY ? bidLock.readLock() : askLock.readLock();

        lock.lock();
        try {
            Long2LongRBTreeMap levels = getLevels(side);
            levels.forEach((k, v) -> {
                writer.printf("%d: %d, ", k, v);
            });
        } finally {
            lock.unlock();
        }
    }

    private Long2LongRBTreeMap getLevels(Side side) {
        return side == Side.BUY ? bids : asks;
    }

    @Override
    public int compareTo(IOrderBook o) {
        if (o == this) {
            return 0;
        }

        if (o == null) {
            return 1;
        }

        if (getLastUpdateTs() != o.getLastUpdateTs()) {
            return (int) (getLastUpdateTs() - o.getLastUpdateTs());
        }

        return (int) (getLastReceivedTs() - o.getLastReceivedTs());
    }

}
