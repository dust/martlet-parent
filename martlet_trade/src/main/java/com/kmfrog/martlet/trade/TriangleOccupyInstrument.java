package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.exec.TacCancelExec;
import com.kmfrog.martlet.trade.exec.TacPlaceOrderExec;
import com.kmfrog.martlet.util.FeedUtils;

import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * 有三角关系的占领式刷量策略。 1. 抢占盘口（比如前三档）。 2. 确认是自己占领（order book & track book)时，立即下对敲单成交。 3.
 * 这个占领策略需要考虑三角盘口汇率换算关系。（hntc/btc,btc/usdt, hntc/usdt)后面以(ca, ab, cb)简称。
 * 
 * @author dust Nov 15, 2019
 *
 */
public class TriangleOccupyInstrument extends Thread implements DataChangeListener {

    protected final Logger logger = LoggerFactory.getLogger(InstrumentSoloDunk.class);

    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();
    private IOrderBook lastBook;
    /** 当前线程主交易对 **/
    Instrument ca;
    Instrument ab;
    /** 有时候，更多的时候应该是bc **/
    Instrument cb;
    Source src;
    TrackBook caTracker;
    Provider provider;
    BrokerApiRestClient client;
    int minSleepMillis;
    int maxSleepMillis;
    long vMin;
    long vMax;

    public TriangleOccupyInstrument(Instrument ca, Instrument ab, Instrument cb, Source src, TrackBook caTracker,
            Provider provider, BrokerApiRestClient client, Map<String, String> args) {
        this.ca = ca;
        this.ab = ab;
        this.cb = cb;
        this.src = src;
        this.caTracker = caTracker;
        this.provider = provider;
        this.client = client;
        minSleepMillis = Integer.parseInt(args.get("minSleepMillis"));
        maxSleepMillis = Integer.parseInt(args.get("maxSleepMillis"));
        vMin = Long.parseLong(args.get("vMin"));
        vMax = Long.parseLong(args.get("vMax"));

    }

    @Override
    public void run() {

        while (!isQuit.get()) {
            long sleepMillis = FeedUtils.between(minSleepMillis, maxSleepMillis);
            try {
                IOrderBook caBook = depthQueue.poll(sleepMillis, TimeUnit.MILLISECONDS);
                if (caBook != null) {
                    if (lastBook != null) {
                        lastBook.destroy();
                        lastBook = null;
                    }
                    lastBook = caBook;

                    boolean placed = false;
                    PriceLevel openAskLevel = caTracker.getBestLevel(Side.SELL);
                    if (openAskLevel != null) {
                        long bestAskPrice = caBook.getBestAskPrice();
                        long bestAskSize = caBook.getAskSize(bestAskPrice);
                        if (openAskLevel.getSize() / bestAskSize * 1.0 >= 0.9) {
                            TacPlaceOrderExec placeBid = new TacPlaceOrderExec(ca, bestAskPrice, bestAskSize, Side.BUY,
                                    client, caTracker);
                            provider.submitExec(placeBid);
                            placed = true;
                        }
                    }

                    PriceLevel openBidLevel = caTracker.getBestLevel(Side.BUY);
                    if (openBidLevel != null) {
                        long bestBidPrice = caBook.getBestBidPrice();
                        long bestBidSize = caBook.getBidSize(bestBidPrice);
                        if (openBidLevel.getSize() / bestBidSize * 1.0 >= 0.9) {
                            TacPlaceOrderExec placeBid = new TacPlaceOrderExec(ca, bestBidPrice, bestBidSize, Side.BUY,
                                    client, caTracker);
                            provider.submitExec(placeBid);
                            placed = true;
                        }
                    }

                    if (placed) {
                        // 处理下一次order book变化及推送。
                        return;
                    }

                    IOrderBook abBook = provider.getOrderBook(src, ab);
                    IOrderBook cbBook = provider.getOrderBook(src, cb);
                    if (abBook == null || cbBook == null) {
                        return;
                    }

                    long diff = max(caBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs())
                            - min(caBook.getLastUpdateTs(), abBook.getLastUpdateTs(), cbBook.getLastUpdateTs());
                    if (diff < C.SYMBOL_DELAY_MILLIS) {
                        return;
                    }

                    long caBid1 = caBook.getBestBidPrice();
                    long caAsk1 = caBook.getBestAskPrice();

                    if (!hasOccupy(Side.SELL, caBook)) {
                        long abAsk1 = abBook.getBestAskPrice();
                        long cbBid1 = cbBook.getBestBidPrice();

                        long caAskLimit = cbBid1 / abAsk1;

                        if ((caAsk1 - caAskLimit) / caAsk1 * 1.0 > 0.001) {
                            // 距离3角套利还有安全距离
                            long price = caAsk1 - ca.getPriceFactor();
                            long volume = FeedUtils.between(vMin, vMax);
                            TacPlaceOrderExec place = new TacPlaceOrderExec(ca, price, volume, Side.SELL, client,
                                    caTracker);
                            provider.submitExec(place);

                        }

                    }

                    if (!hasOccupy(Side.BUY, caBook)) {
                        long abBid1 = abBook.getBestBidPrice();
                        long cbAsk1 = cbBook.getBestAskPrice();

                        long caBidLimit = cbAsk1 / abBid1;

                        if ((caBidLimit - caBid1) / caBid1 * 1.0 > 0.001) {
                            // 距离3角套利还有安全距离
                            long price = caBid1 - ca.getPriceFactor();
                            long volume = FeedUtils.between(vMin, vMax);
                            TacPlaceOrderExec place = new TacPlaceOrderExec(ca, price, volume, Side.BUY, client,
                                    caTracker);
                            provider.submitExec(place);
                        }
                    }

                    // 撤掉3档以外所有订单。
                    cancelAfterLevel3Ask(caBook);
                    cancelAfterLevel3Bid(caBook);

                }

            } catch (InterruptedException ex) {
                logger.warn(ex.getMessage(), ex);
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
    }

    private void cancelAfterLevel3Ask(IOrderBook caBook) {
        LongSortedSet prices = caBook.getAskPrices();
        int i = 0;
        long level3 = 0;
        for (LongBidirectionalIterator iter = prices.iterator(); iter.hasNext() && i < 3;) {
            level3 = iter.nextLong();
            i++;
        }

        Set<Long> afterLevel3 = caTracker.getOrdersBetter(Side.SELL, level3);
        TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, caTracker);
        provider.submitExec(cancelExec);
    }

    private void cancelAfterLevel3Bid(IOrderBook caBook) {
        LongSortedSet prices = caBook.getBidPrices();
        int i = 0;
        long level3 = 0;
        for (LongBidirectionalIterator iter = prices.iterator(); iter.hasPrevious() && i < 3;) {
            level3 = iter.previousLong();
            i++;
        }

        Set<Long> afterLevel3 = caTracker.getOrdersBetter(Side.BUY, level3);
        TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, caTracker);
        provider.submitExec(cancelExec);
    }

    /**
     * 某价位是否已经被占领？
     * 
     * @param side
     * @param price
     * @param obSize
     * @return
     */
    boolean hasOccupy(Side side, IOrderBook book) {
        PriceLevel openLevel = caTracker.getBestLevel(side);
        if (openLevel == null) {
            return false;
        }

        long bboPrice = side == Side.SELL ? book.getBestAskPrice() : book.getBestBidPrice();
        long bboSize = side == Side.SELL ? book.getAskSize(bboPrice) : book.getBidSize(bboPrice);
        return openLevel.getSize() / bboSize * 1.0 >= 0.9;
    }

    static long min(long... numbers) {
        if (numbers.length == 1) {
            return numbers[0];
        }
        long min = Math.min(numbers[0], numbers[1]);
        for (int i = 2; i < numbers.length; i++) {
            min = Math.min(min, numbers[i]);
        }
        return min;
    }

    static long max(long... numbers) {
        if (numbers.length == 1) {
            return numbers[0];
        }
        long max = Math.max(numbers[0], numbers[1]);
        for (int i = 2; i < numbers.length; i++) {
            max = Math.min(max, numbers[i]);
        }
        return max;
    }

    @Override
    public void onDepth(Long instrument, IOrderBook book) {
        try {
            depthQueue.put(book);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onTrade(Long instrument, TradeLog tradeLog) {
        // TODO Auto-generated method stub

    }

}
