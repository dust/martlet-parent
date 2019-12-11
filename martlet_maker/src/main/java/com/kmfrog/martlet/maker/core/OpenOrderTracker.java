package com.kmfrog.martlet.maker.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderEntry;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.maker.exec.CheckTradeOrderExec;
import com.kmfrog.martlet.maker.exec.CheckVolumeExec;
import com.kmfrog.martlet.maker.model.entity.Order;
import com.kmfrog.martlet.maker.service.DepthService;

/**
 * 开放订单跟踪器。
 * 
 * @author dust Nov 16, 2019
 *
 */
public class OpenOrderTracker extends Thread {

    final Logger logger = LoggerFactory.getLogger(OpenOrderTracker.class);
    final List<Instrument> instruments;
    final ReentrantLock lock = new ReentrantLock();
    final Source src;
    final Provider provider;
    final DepthService depthService;

    private AtomicBoolean isQuit = new AtomicBoolean(false);

    public OpenOrderTracker(Source src, List<Instrument> instruments, Provider provider) {
        super(String.format("%s Open Order Tracker", src.name()));
        this.src = src;
        this.instruments = instruments == null ? new ArrayList<>() : instruments;
        this.provider = provider;
        this.depthService = provider.getDepthService();
    }

    public void setup(Collection<Instrument> allNew) {
        lock.lock();
        try {
            List<Instrument> addNew = allNew.stream().filter(v -> !instruments.contains(v))
                    .collect(Collectors.toList());
            List<Instrument> rmNew = instruments.stream().filter(v -> !allNew.contains(v)).collect(Collectors.toList());
            instruments.addAll(addNew);
            instruments.removeAll(rmNew);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void run() {

        while (!isQuit.get()) {
            try {
                Thread.sleep(provider.getOpenOrderSleepMillis());
                lock.lock();
                try {
                    for (Instrument instrument : instruments) {
                        TrackBook trackBook = provider.getTrackBook(src, instrument);
                        SymbolAoWithFeatureAndExtra symbolInfo = provider.getSymbolInfo(instrument);
                        List<Order> openBids = depthService.getOpenOrders(instrument.asString(), Side.BUY,
                                provider.getBuyRobotId(symbolInfo));
                        List<Order> openAsks = depthService.getOpenOrders(instrument.asString(), Side.SELL,
                                provider.getBuyRobotId(symbolInfo));
                        // System.out.println("openBids:"+openBids+"\nopenAsk:"+openAsks);
                        trackOpenOrders(instrument, trackBook, openBids, openAsks);
                        provider.submit(new CheckVolumeExec(instrument, provider, trackBook, logger));

                        provider.submit(new CheckTradeOrderExec(src, instrument, symbolInfo, provider, logger));
                    }
                } catch (Exception ex) {
                    logger.warn(ex.getMessage(), ex);
                } finally {
                    lock.unlock();
                }

            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);

            }
        }
    }

    private void trackOpenOrders(Instrument instrument, TrackBook book, List<Order> openBids, List<Order> openAsks) {
        Set<Long> openBidSet = openBids.stream().map(ord -> ord.getId()).collect(Collectors.toSet());
        Set<Long> openAskSet = openAsks.stream().map(ord -> ord.getId()).collect(Collectors.toSet());
        Set<Long> trackBidSet = book.getOrders(Side.BUY);
        Set<Long> trackAskSet = book.getOrders(Side.SELL);
        // System.out.println("openBidSet:"+openBidSet);
        // System.out.println("trackBidSet:"+trackBidSet);
        // System.out.println("openAskSet:"+openAskSet);
        // System.out.println("trackAskSet"+trackAskSet);
//        if (logger.isInfoEnabled()) {
//            logger.info("{} {} trackBook bids: {} | {}", src, instrument.asString(), book.dump(Side.BUY), openBidSet);
//            logger.info("{} {} trackBook asks: {} | {}", src, instrument.asString(), book.dump(Side.SELL), openAskSet);
//        }

        Set<Long> closedBidSet = Sets.difference(trackBidSet, openBidSet);
        Set<Long> closedAskSet = Sets.difference(trackAskSet, openAskSet);
        if (closedBidSet.size() > 0) {
            closedBidSet.forEach(orderId -> {
                closeOrder(instrument, book, orderId, Side.BUY);
            }); // 已成交/撤买单
        }

        if (closedAskSet.size() > 0) {
            closedAskSet.forEach(orderId -> {
                closeOrder(instrument, book, orderId, Side.SELL);
            }); // 已成交/撤卖单
        }

        Map<Long, Order> bidOrders = null;
        Map<Long, Order> askOrders = null;
        // 新增订单（数据库有，trackBook中没有)
        Set<Long> appendBidSet = Sets.difference(openBidSet, trackBidSet);
        Set<Long> appendAskSet = Sets.difference(openAskSet, trackAskSet);
        if (appendBidSet.size() > 0 || appendAskSet.size() > 0) {

            bidOrders = openBids.stream().collect(Collectors.toMap(Order::getId, v -> v));
            askOrders = openAsks.stream().collect(Collectors.toMap(Order::getId, v -> v));
            appendOpenOrder(instrument, book, appendBidSet, bidOrders);
            appendOpenOrder(instrument, book, appendAskSet, askOrders);
        }

        // 交集
        Set<Long> unionBidSet = Sets.union(openBidSet, trackBidSet);
        Set<Long> unionAskSet = Sets.union(openAskSet, trackAskSet);
        if (unionBidSet.size() > 0 || unionAskSet.size() > 0) {

            if (bidOrders == null) {
                bidOrders = openBids.stream().collect(Collectors.toMap(Order::getId, v -> v));
            }
            if (askOrders == null) {
                askOrders = openAsks.stream().collect(Collectors.toMap(Order::getId, v -> v));
            }

            syncOpenOrder(instrument, book, Side.BUY, unionBidSet, bidOrders);
            syncOpenOrder(instrument, book, Side.SELL, unionAskSet, askOrders);
        }

    }

    private void closeOrder(Instrument instrument, TrackBook book, Long orderId, Side side) {
        book.remove(orderId);
    }

    private static void appendOpenOrder(Instrument instrument, TrackBook book, Set<Long> openOrderIds,
            Map<Long, Order> orders) {
        openOrderIds.forEach(orderId -> {
            Order order = orders.get(orderId);
            if (order != null) {
                BigDecimal decPrice = order.getPrice();
                BigDecimal decSize = order.getVolume().subtract(order.getDealVolume());
                int status = order.getStatus();
                long price = decPrice.multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
                long size = decSize.multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
                book.entry(orderId, order.getSide(), price, size, status);
            }
        });
    }

    private void syncOpenOrder(Instrument instrument, TrackBook book, Side side, Set<Long> unionSet,
            Map<Long, Order> orders) {
        unionSet.forEach(orderId -> {
            Order order = orders.get(orderId);
            if (order != null) {
                BigDecimal decPrice = order.getPrice();
                BigDecimal decSize = order.getVolume().subtract(order.getDealVolume());
                int status = order.getStatus();
                long price = decPrice.multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
                long volume = decSize.multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
                OrderEntry entry = book.getOrder(orderId.longValue());
                if (entry != null) {
                    if (entry.getLevel().getPrice() != price) {
                        logger.info("{} {} trackbook out of sync: {},{} {},{}", instrument.asString(), orderId,
                                entry.getStatus(), status, decPrice, entry.getLevel().getPrice());
                    }
                    entry.setStatus(status);
                    entry.resize(volume);
                }
            }
        });
    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    public void destroy() {
    }

}
