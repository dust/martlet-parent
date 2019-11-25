package com.kmfrog.martlet.trade;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderEntry;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.Order;
import io.broker.api.client.domain.account.OrderSide;
import io.broker.api.client.domain.account.request.OpenOrderRequest;

/**
 * 开放订单跟踪器。 附带了帐户总体平衡功能。----今后再重构。
 * 
 * @author dust Nov 16, 2019
 *
 */
public class OpenOrderTracker extends Thread {

    final Logger logger = LoggerFactory.getLogger(OpenOrderTracker.class);
    BrokerApiRestClient client;
    Instrument[] instruments;
    Source src;
    Provider provider;
    private AtomicBoolean isQuit = new AtomicBoolean(false);

    public OpenOrderTracker(Source src, Instrument[] instruments, BrokerApiRestClient client, Provider provider) {
        super(String.format("%s Open Order Tracker", src.name()));
        this.src = src;
        this.instruments = instruments;
        this.client = client;
        this.provider = provider;
    }

    @Override
    public void run() {

        while (!isQuit.get()) {
            try {
                Thread.sleep(5000);
                for (Instrument instrument : instruments) {
                    TrackBook trackBook = provider.getTrackBook(src, instrument);
                    OpenOrderRequest req = new OpenOrderRequest();
                    req.setSymbol(instrument.asString());
                    List<Order> openOrders = client.getOpenOrders(req);
                    trackOpenOrders(instrument, trackBook, openOrders);
                    System.out.println(instrument.asString() + ".openAsks: " + trackBook.getOrders(Side.SELL)
                            + " .openBid" + trackBook.getOrders(Side.BUY));
                }
                handleImbalance();

            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);

            }
        }
    }

    private void handleImbalance() {

    }

    private void trackOpenOrders(Instrument instrument, TrackBook book, List<Order> openOrders) {
        Set<Long> openBidSet = openOrders.stream().filter(order -> order.getSide() == OrderSide.BUY)
                .map(ord -> ord.getOrderId()).collect(Collectors.toSet());
        Set<Long> openAskSet = openOrders.stream().filter(order -> order.getSide() == OrderSide.SELL)
                .map(ord -> ord.getOrderId()).collect(Collectors.toSet());
        Set<Long> trackBidSet = book.getOrders(Side.BUY);
        Set<Long> trackAskSet = book.getOrders(Side.SELL);

        Set<Long> filledBidSet = Sets.difference(trackBidSet, openBidSet);
        Set<Long> filledAskSet = Sets.difference(trackAskSet, openAskSet);
        if (filledBidSet.size() > 0) {
            filledBidSet.forEach(orderId -> {
                appendTradeLog(instrument, book, orderId, Side.BUY);
            }); // 已成交买单
        }

        if (filledAskSet.size() > 0) {
            filledAskSet.forEach(orderId -> {
                appendTradeLog(instrument, book, orderId, Side.SELL);
            }); // 已成交卖单
        }

        Set<Long> appendBidSet = Sets.difference(openBidSet, trackBidSet);
        Set<Long> appendAskSet = Sets.difference(openAskSet, trackAskSet);
        if (appendBidSet.size() > 0 || appendAskSet.size() > 0) {
            Map<Long, Order> orders = openOrders.stream().collect(Collectors.toMap(Order::getOrderId, v -> v));
            appendOpenOrder(instrument, book, appendBidSet, orders);
            appendOpenOrder(instrument, book, appendAskSet, orders);
        }

    }

    private void appendTradeLog(Instrument instrument, TrackBook book, Long orderId, Side side) {
        RollingTimeSpan<TradeLog> logs = provider.getRollingTradeLog(src, instrument);
        OrderEntry orderEntry = book.getOrder(orderId.longValue());
        long price = orderEntry.getLevel().getPrice();
        long now = System.currentTimeMillis();
        long size = orderEntry.getRemainingQuantity();
        logs.add(new TradeLog(src, instrument.asLong(), orderId, price, size, 0L, false, now, now));
        book.remove(orderId);
    }

    private void appendOpenOrder(Instrument instrument, TrackBook book, Set<Long> appendBidSet,
            Map<Long, Order> orders) {
        appendBidSet.forEach(orderId -> {
            Order order = orders.get(orderId);
            BigDecimal decPrice = new BigDecimal(order.getPrice());
            BigDecimal decSize = new BigDecimal(order.getOrigQty());
            long price = decPrice.multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
            long size = decSize.multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
            book.entry(orderId, order.getSide() == OrderSide.BUY ? Side.BUY : Side.SELL, price, size);
        });
    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    public void destroy() {
    }

}
