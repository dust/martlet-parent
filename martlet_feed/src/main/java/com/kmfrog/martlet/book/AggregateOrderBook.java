package com.kmfrog.martlet.book;

import static com.kmfrog.martlet.C.SEPARATOR;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.util.Fmt;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

/**
 * An aggregate order book(Thread safety. 多源订单簿。它是线程安全的。
 * 
 * @author dust Sep 30, 2019
 *
 */
public class AggregateOrderBook implements IOrderBook {

    private final long instrument;
    private final Long2ObjectRBTreeMap<MultiSrc> bids;
    private final Long2ObjectRBTreeMap<MultiSrc> asks;
    private final ReadWriteLock bidLock;
    private final ReadWriteLock askLock;
    private final AtomicLong lastUpdate;
    private final AtomicLong lastReceived;

    public AggregateOrderBook(long instrument) {
        this.instrument = instrument;
        lastUpdate = new AtomicLong(0L);
        lastReceived = new AtomicLong(0L);

        this.bids = new Long2ObjectRBTreeMap<MultiSrc>(LongComparators.OPPOSITE_COMPARATOR);
        this.asks = new Long2ObjectRBTreeMap<MultiSrc>(LongComparators.NATURAL_COMPARATOR);

        this.bidLock = new ReentrantReadWriteLock();
        this.askLock = new ReentrantReadWriteLock();
    }

    /**
     * Get the instrument
     * 
     * @return
     */
    @Override
    public long getInstrument() {
        return instrument;
    }

    /**
     * Is the bid order book empty?
     * 
     * @return
     */
    public boolean isBidEmpty() {
        bidLock.readLock().lock();
        try {
            return bids.isEmpty();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Get the best bid price.
     * 
     * @return
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
     * Get the bid prices.
     * 
     * @return
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
     * @param price
     * @return
     */
    @Override
    public long getBidSize(long price) {
        bidLock.readLock().lock();
        try {
            return bids.get(price).size();
        } finally {
            bidLock.readLock().unlock();
        }
    }

    /**
     * Is the ask order book empty?
     * 
     * @return
     */
    public boolean isAskEmpty() {
        askLock.readLock().lock();
        try {
            return asks.isEmpty();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Get the best ask price.
     * 
     * @return
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

    /**
     * Get the ask prices;
     * 
     * @return
     */
    public long getBestAskPrices() {
        bidLock.readLock().lock();
        try {
            if (asks.isEmpty()) {
                return 0;
            }
            return asks.firstLongKey();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Get an ask level size.
     */
    @Override
    public long getAskSize(long price) {
        askLock.readLock().lock();
        try {
            return asks.get(price).size();
        } finally {
            askLock.readLock().unlock();
        }
    }

    /**
     * Get the ask prices.
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
        // 对于聚合订单簿，每次更新接收时间，但是平台最后更新时间，只能是更新的时间才被更新。即只单向（大）修改，不会回撤。避免时间混乱。
        lastReceived.set(System.currentTimeMillis());
        long currTs = lastUpdate.get();
        if (currTs < ts) {
            lastUpdate.compareAndSet(currTs, ts);
        }
    }

    @Override
    public boolean replace(Side side, long price, long quantity, int source) {
        // bids or asks
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);

            if (!levels.containsKey(price)) {
                if (quantity > 0) {
                    MultiSrc multiSrc = new MultiSrc(price);
                    multiSrc.updateTo(quantity, source);
                    levels.put(price, multiSrc);
                }
            } else {
                long newSize = levels.get(price).updateTo(quantity, source);
                if (newSize <= 0) {
                    levels.remove(price);
                }
            }

            return levels.size() > 0 && price == levels.firstLongKey();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean incr(Side side, long price, long quantity, int source) {
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);

            if (!levels.containsKey(price)) {
                // 如果order book中不存在数量为零的此价位，那么也没有意义进行添加。
                if (quantity > 0L) {
                    MultiSrc multiSrc = new MultiSrc(price);
                    multiSrc.addTo(quantity, source);
                    levels.put(price, multiSrc);
                }
            } else {
                long newSize = levels.get(price).addTo(quantity, source);
                if (newSize <= 0) {
                    levels.remove(price);
                }
            }

            return levels.size() > 0 && price == levels.firstLongKey();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理某个来源所有订单（价格，数量）。
     * 
     * @param side
     * @param source
     * @return
     */
    @Override
    public boolean clear(Side side, int source) {
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();

        lock.lock();
        try {
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);
            if (levels.size() == 0) {
                return false;
            }

            long oldPrice = levels.firstLongKey();
            levels.forEach((k, v) -> {
                if (v.clearSource(source)) {
                    levels.remove(k.longValue());
                }
            });

            return levels.size() == 0 || oldPrice != levels.firstLongKey();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 聚合其它order book.
     * 
     * @param src
     * @param book
     */
    public void aggregate(int src, IOrderBook book) {
        aggregate(Side.BUY, src, book);
        aggregate(Side.SELL, src, book);
    }

    private void aggregate(Side side, int source, IOrderBook book) {
        Lock lock = side == Side.BUY ? bidLock.writeLock() : askLock.writeLock();
        LongSortedSet prices = side == Side.BUY ? book.getBidPrices() : book.getAskPrices();

        lock.lock();
        try {
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);
            prices.stream().forEach(price -> {
                long quantity = side == Side.BUY ? book.getBidSize(price.longValue())
                        : book.getAskSize(price.longValue());
                if (!levels.containsKey(price.longValue())) {
                    // 如果order book中不存在数量为零的此价位，那么也没有意义进行添加。
                    if (quantity > 0L) {
                        MultiSrc multiSrc = new MultiSrc(price);
                        multiSrc.addTo(quantity, source);
                        levels.put(price.longValue(), multiSrc);
                    }
                } else {
                    long newSize = levels.get(price.longValue()).addTo(quantity, source);
                    if (newSize <= 0) {
                        levels.remove(price.longValue());
                    }
                }

            });
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
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);
            int index = 0;
            for (ObjectBidirectionalIterator<Long2ObjectMap.Entry<MultiSrc>> iter = levels.long2ObjectEntrySet()
                    .iterator(); iter.hasNext() && index < maxLevel;) {

                Long2ObjectMap.Entry<MultiSrc> entry = iter.next();
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                MultiSrc v = entry.getValue();
                sb.append('[').append(Fmt.fmtNum(entry.getLongKey(), pricePrecision)).append(SEPARATOR)
                        .append(Fmt.fmtNum(v.size(), volumePrecision)).append(SEPARATOR).append(v.dumpPlainText(volumePrecision))
                        .append(']');
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
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);
            int index = 0;
            for (ObjectBidirectionalIterator<Long2ObjectMap.Entry<MultiSrc>> iter = levels.long2ObjectEntrySet()
                    .iterator(); iter.hasNext() && index < maxLevel;) {

                Long2ObjectMap.Entry<MultiSrc> entry = iter.next();
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                MultiSrc v = entry.getValue();
                sb.append('[').append(entry.getLongKey()).append(SEPARATOR)
                        .append(v.size()).append(SEPARATOR).append(v.dumpOriginText())
                        .append(']');
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
            Long2ObjectRBTreeMap<MultiSrc> levels = getLevels(side);
            levels.forEach((k, v) -> {
                writer.printf("%d:[%s], ", k, v.dumpString());
            });
        } finally {
            lock.unlock();
        }
    }

    private Long2ObjectRBTreeMap<MultiSrc> getLevels(Side side) {
        return side == Side.BUY ? bids : asks;
    }

}
