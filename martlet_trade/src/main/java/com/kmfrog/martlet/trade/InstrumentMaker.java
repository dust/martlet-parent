package com.kmfrog.martlet.trade;

import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;

/**
 * 基于外部市场深度来源而进行做市的执行者。
 * 
 * @author dust Nov 15, 2019
 *
 */
public class InstrumentMaker extends Thread implements DataChangeListener {

    final Logger logger = LoggerFactory.getLogger(InstrumentMaker.class);
    private final Instrument instrument;
    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();

    private final TrackBook trackBook;
    private final Provider provider;
    /**
     * 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿。
     **/
    private final Map<Source, IOrderBook> multiSrcBooks;
    private AggregateOrderBook aggBook;

    public InstrumentMaker(Instrument instrument, TrackBook trackBook, Provider provider) {
        this.instrument = instrument;
        this.trackBook = trackBook;
        this.provider = provider;
        multiSrcBooks = new ConcurrentHashMap<>();
    }

    public void run() {

        while (!isQuit.get()) {
            try {
                IOrderBook book = depthQueue.take();
                Source src = Source.values()[(int) book.getSourceValue()];
                // if (book.getInstrument() == instrument.asLong()) {
                if (src == Source.Mix) {
                    if (aggBook != null) {
                        aggBook.destroy();
                        aggBook = null;
                    }
                    aggBook = (AggregateOrderBook) book;
                } else {
//                    IOrderBook old = multiSrcBooks.put(src, book);
//                    if (old != null) {
//                        old.destroy();
//                        old = null;
//                    }
                    System.out.println(book.getOriginText(src, provider.getMaxLevel()));
                    provider.setOrderBook(src, instrument, book);
                    
                }
                // }

                // long[] bbo = getBBO();
                // List<Long> lst = new ArrayList<>();
                // lst.add(System.currentTimeMillis());
                // lst.add(book.getLastUpdateTs());
                // lst.add(book.getSourceValue());
                // lst.add(bbo == null ? -1 : bbo[0]);
                // lst.add(bbo == null ? -1 : bbo[1]);
                // if (src != Source.Mix) {
                //// RollingTimeSpan<TradeLog> avgTradeLogs = provider.getAvgTrade(src, instrument);
                //// lst.add(avgTradeLogs.avg());
                //// lst.add(avgTradeLogs.last());
                // }
                //
                // System.out.println(lst);
                //
                // // 得到最糟糕的买单。>bbo[0](${bid})，逆序：从大到小。
                // Set<Long> worstBidOrders = trackBook.getOrdersBetween(Side.BUY, Long.MAX_VALUE, bbo[0]);
                // // 得到最糟糕的卖单。<bbo[1](${ask})，顺序：从小到大。
                // Set<Long> worstAskOrders = trackBook.getOrdersBetween(Side.SELL, 0, bbo[1]);

            } catch (InterruptedException ex) {
                logger.warn(ex.getMessage(), ex);
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);
            }

        }

    }

    public IOrderBook getOrderBook(Source src) {
        return multiSrcBooks.computeIfAbsent(src, key -> src == Source.Mix ? new AggregateOrderBook(instrument.asLong())
                : new OrderBook(src, instrument.asLong()));
    }

    /**
     * 获得最优出价和供应。（Best Bid and Offer),
     * 
     * @return [${bid}, ${offer}]
     */
    public long[] getBBO() {
        long floatDownward = 1000 - provider.getSpreadLowLimitMillesimal() / 2;
        long floatUpward = 1000 + provider.getSpreadLowLimitMillesimal() / 2;
        if (C.PREFER_SOURCE_NAME != null) {
            IOrderBook book = multiSrcBooks.get(Source.valueOf(C.PREFER_SOURCE_NAME));
            if (book == null) {
                return null;
            }

            return new long[] { book.getBestBidPrice() * floatDownward / 1000,
                    book.getBestAskPrice() * floatUpward / 1000 };
        }

        Long2LongMap bidMap = new Long2LongArrayMap();
        Long2LongMap askMap = new Long2LongArrayMap();
        Long2LongMap tsMap = new Long2LongArrayMap();

        Set<Source> sources = multiSrcBooks.keySet();
        for (Source src : sources) {
            IOrderBook book = multiSrcBooks.get(src);
            bidMap.put(src.ordinal(), book.getBestBidPrice());
            askMap.put(src.ordinal(), book.getBestAskPrice());
            tsMap.put(src.ordinal(), book.getLastUpdateTs());
        }

        long[] tsArray = tsMap.values().toLongArray();
        LongSummaryStatistics tsStats = LongStream.of(tsArray).summaryStatistics();
        long min = tsStats.getMin();
        long max = tsStats.getMax();
        if (max - min > C.SYMBOL_DELAY_MILLIS) {
            long src = findSourceValue(tsMap, min, sources);
            if (src > Source.Mix.ordinal()) { // Source.Mix == 0
                bidMap.remove(src);
                askMap.remove(src);
            }
        }

        long[] bidArray = bidMap.values().toLongArray();
        LongSummaryStatistics bidStats = LongStream.of(bidArray).summaryStatistics();
        long avg = bidStats.getSum() / bidStats.getCount();
        min = bidStats.getMin();
        max = bidStats.getMax();
        if ((max - min) / (avg * 1.0) > C.SYMBOL_PRICE_DIFF) {
            long diffSrc = findSourceValue(bidMap, max - avg > avg - min ? max : min, sources);
            if (diffSrc > Source.Mix.ordinal()) {
                bidMap.remove(diffSrc);
                askMap.remove(diffSrc);
            }
        }

        long[] askArray = askMap.values().toLongArray();
        LongSummaryStatistics askStats = LongStream.of(askArray).summaryStatistics();
        avg = askStats.getSum() / askStats.getCount();
        min = askStats.getMin();
        max = askStats.getMax();
        if ((max - min) / (avg * 1.0) > C.SYMBOL_PRICE_DIFF) {
            long diffSrc = findSourceValue(askMap, max - avg > avg - min ? max : min, sources);
            if (diffSrc > Source.Mix.ordinal()) {
                bidMap.remove(diffSrc);
                askMap.remove(diffSrc);
            }
        }

        askArray = askMap.values().toLongArray();
        bidArray = bidMap.values().toLongArray();
        if (askArray.length == 0 || bidArray.length == 0) {
            return null;
        }

        askStats = LongStream.of(askArray).summaryStatistics();
        bidStats = LongStream.of(bidArray).summaryStatistics();

        return new long[] { bidStats.getSum() / bidStats.getCount() * floatDownward / 2000,
                askStats.getSum() / askStats.getCount() * floatUpward / 2000 };

    }

    private static long findSourceValue(Long2LongMap map, long v, Set<Source> sources) {
        for (Source src : sources) {
            if (map.get(src.ordinal()) == v) {
                return src.ordinal();
            }
        }
        return -1L;
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

    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    public void destroy() {
        depthQueue.clear();
    }

}
