package com.kmfrog.martlet.book;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongComparators;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.fastutil.longs.LongSortedSets;
import com.kmfrog.martlet.book.*;

/**
 * 开放订单簿。可用于交易中的订单管理。 `entry, remove, cancel`
 * 
 * @author dust Nov 18, 2019
 *
 */
public class TrackBook {

    private final Instrument instrument;
    private final Long2ObjectRBTreeMap<PriceLevel> bids;
    private final Long2ObjectRBTreeMap<PriceLevel> asks;

    private final Long2ObjectOpenHashMap<OrderEntry> orders;
    private final ReentrantReadWriteLock lock;

    public TrackBook(Instrument instrument) {
        this.instrument = instrument;
        lock = new ReentrantReadWriteLock();
        bids = new Long2ObjectRBTreeMap<>(LongComparators.OPPOSITE_COMPARATOR);
        asks = new Long2ObjectRBTreeMap<>(LongComparators.NATURAL_COMPARATOR);

        orders = new Long2ObjectOpenHashMap<>();
    }

    public Instrument getInstrument() {
        return instrument;
    }

//    public void entry(long orderId, Side side, long price, long size) {
//        lock.writeLock().lock();
//        try {
//            if (orders.containsKey(orderId)) {
//                return;
//            }
//
//            if (side == Side.BUY) {
//                orders.put(orderId, add(bids, orderId, side, price, size);
//            } else {
//                orders.put(orderId, add(asks, orderId, side, price, size));
//            }
//        } finally {
//            lock.writeLock().unlock();
//        }
//    }
    
    public void entry(long orderId, Side side, long price, long size, int status, String clientOrderId) {
        lock.writeLock().lock();
        try {
            if (orders.containsKey(orderId)) {
                return;
            }

            if (side == Side.BUY) {
                orders.put(orderId, add(bids, orderId, side, price, size, status, clientOrderId));
            } else {
                orders.put(orderId, add(asks, orderId, side, price, size, status, clientOrderId));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    public void entry(long orderId, Side side, long price, long size, int status) {
    	entry(orderId, side, price, size, status, "");
    }

    public void newSize(long orderId, Side side, long price, long remainingQuantity) {
        lock.writeLock().lock();
        try {
            OrderEntry order = orders.get(orderId);
            if (order == null) {
                return;
            }

            order.resize(remainingQuantity);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 撤销某侧指定价位的订单。
     * 
     * @param orderId
     * @param side
     * @param price
     * @param size
     */
    public void cancel(long orderId, Side side, long price, long size) {
        lock.writeLock().lock();
        try {
            OrderEntry order = orders.get(orderId);
            if (order == null) {
                return;
            }

            long remainingQuantity = order.getRemainingQuantity();
            if (size >= remainingQuantity) { // 参数错误。
                return;
            }

            delete(order);
            orders.remove(orderId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(long orderId) {
        lock.writeLock().lock();
        try {
            OrderEntry order = orders.get(orderId);
            if (order != null) {
                delete(order);
            }
            orders.remove(orderId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获得某价位区间的全部订单（指定的某侧)。
     * 
     * @param side
     * @param from inclusive
     * @param to exclusive
     * @return
     */
    public Set<Long> getOrdersBetween(Side side, long from, long to) {
        lock.readLock().lock();
        try {
            Set<Long> set = new HashSet<>();
            Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;

            if (!levels.isEmpty()) {
                LongSortedSet prices = levels.subMap(from, to).keySet();
                prices.stream().forEach(p -> {
                    set.addAll(levels.get(p.longValue()).getOrderIds());
                });
            }
            return set;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Long> getOrders(Side side) {
        Set<Long> set = new HashSet<>();
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
        lock.readLock().lock();
        try {
            if (!levels.isEmpty()) {
                LongSortedSet prices = levels.keySet();
                prices.stream().forEach(p -> set.addAll(levels.get(p.longValue()).getOrderIds()));
            }

        } finally {
            lock.readLock().unlock();
        }
        return set;
    }

    /**
     * 获得某一侧不包含指定状态的订单id集合。
     * 
     * @param side
     * @param excludeStatus
     * @return
     */
    public Set<Long> getOrders(Side side, int excludeStatus) {
        Set<Long> set = new HashSet<>();
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
        lock.readLock().lock();
        try {
            if (!levels.isEmpty()) {
                LongSortedSet prices = levels.keySet();
                prices.stream().forEach(p -> set.addAll(levels.get(p.longValue()).getOrderIds(excludeStatus)));
            }

        } finally {
            lock.readLock().unlock();
        }
        return set;
    }

    /**
     * 获得某个价格的档位信息。
     * 
     * @param side
     * @param price
     * @return
     */
    public PriceLevel getPriceLevel(Side side, long price) {
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
        lock.readLock().lock();
        try {
            return levels.get(price);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获得某一侧的全部订单价格。
     * 
     * @param side
     * @return
     */
    public LongSortedSet getPrices(Side side) {
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
        lock.readLock().lock();
        try {
            if (levels.isEmpty()) {
                return LongSortedSets.EMPTY_SET;
            }
            return levels.keySet();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获得优于指定价位的全部订单（指定的某侧)。
     * 
     * @param side
     * @param from inclusive
     * @return
     */
    public Set<Long> getBetterOrders(Side side, long from) {
        Set<Long> set = new HashSet<>();
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;

        lock.readLock().lock();
        try {
            // 最差的报价：指定买入价格比订单簿中最差出价（即最低买价格）还低； 指定卖出价比订单簿最差出价（即最高卖价）还高。
            if (!levels.isEmpty()) {
                long worst = levels.lastLongKey();
                if ((side == Side.BUY && from < worst) || (side == Side.SELL && from > worst)) {
                    return new HashSet<Long>();
                }

                LongSortedSet prices = levels.keySet().tailSet(from);
                prices.stream().forEach(p -> {
                    set.addAll(levels.get(p.longValue()).getOrderIds());
                });
            }
            return set;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获得优于指定价位且不包含指定状态的订单集合（指定的某侧)。
     * 
     * @param side
     * @param from inclusive
     * @return
     */
    public Set<Long> getBetterOrders(Side side, long from, int excludeStatus) {
        Set<Long> set = new HashSet<>();
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;

        lock.readLock().lock();
        try {
            // 最差的报价：指定买入价格比订单簿中最差出价（即最低买价格）还低； 指定卖出价比订单簿最差出价（即最高卖价）还高。
            if (!levels.isEmpty()) {
                long worst = levels.lastLongKey();
                if ((side == Side.BUY && from < worst) || (side == Side.SELL && from > worst)) {
                    return new HashSet<Long>();
                }

                LongSortedSet prices = levels.keySet().tailSet(from);
                prices.stream().forEach(p -> {
                    set.addAll(levels.get(p.longValue()).getOrderIds(excludeStatus));
                });
            }
            return set;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获得更糟糕的买
     * 
     * @param side
     * @param from
     * @return
     */
    public Set<Long> getWorseOrders(Side side, long from) {
        Set<Long> set = new HashSet<>();
        Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;

        lock.readLock().lock();
        try {
            if (levels.isEmpty()) {
                return set;
            }

            LongSortedSet prices = levels.keySet().headSet(from);
            prices.stream().forEach(p -> set.addAll(levels.get(p.longValue()).getOrderIds()));
            return set;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void delete(OrderEntry order) {
        PriceLevel level = order.getLevel();

        level.delete(order);
        if (level.isEmpty()) {
            delete(level);
        }
    }

    private void delete(PriceLevel level) {
        switch (level.getSide()) {
        case BUY:
            bids.remove(level.getPrice());
            break;
        case SELL:
            asks.remove(level.getPrice());
            break;
        }
    }

    private static OrderEntry add(Long2ObjectRBTreeMap<PriceLevel> levels, long orderId, Side side, long price,
            long size, int status, String clientOrderId) {
        PriceLevel level = levels.get(price);
        if (level == null) {
            level = new PriceLevel(side, price);
            levels.put(price, level);
        }

        return level.add(orderId, size, status, clientOrderId);
    }

    /**
     * 获得指定某侧最优档位。
     * 
     * @param side
     * @return
     */
    public PriceLevel getBestLevel(Side side) {
        lock.readLock().lock();
        try {
            Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
            if (levels.isEmpty()) {
                return null;
            }
            return levels.get(levels.firstLongKey());
        } finally {
            lock.readLock().unlock();
        }
    }

    public OrderEntry getOrder(long orderId) {
        lock.readLock().lock();
        try {
            return orders.get(orderId);
        } finally {
            lock.readLock().unlock();
        }

    }

    public String dump(Side side) {
        lock.readLock().lock();
        try {
            Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
            return levels.toString();
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 获得优于指定价位的全部订单（指定的某侧)。
     * 
     * @param side
     * @param from inclusive
     * @return
     */
    public Set<Long> getOrdersBetter(Side side, long from){
        lock.readLock().lock();
        try {
            Set<Long> set = new HashSet<>();
            Long2ObjectRBTreeMap<PriceLevel> levels = side == Side.BUY ? bids : asks;
            //最差的报价：指定买入价格比订单簿中最差出价（即最低买价格）还低； 指定卖出价比订单簿最差出价（即最高卖价）还高。
            long worst = levels.lastLongKey();
            if((side==Side.BUY && from < worst) || (side==Side.SELL && from > worst)) {
                return new HashSet<Long>();
            }

            if (!levels.isEmpty()) {
                LongSortedSet prices = levels.subMap(from, worst).keySet();
                prices.stream().forEach(p -> {
                    set.addAll(levels.get(p.longValue()).getOrderIds());
                });
            }
            return set;
        } finally {
            lock.readLock().unlock();
        }
    }

}
