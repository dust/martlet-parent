package com.kmfrog.martlet.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;

public class InstrumentMaker extends Thread implements DataChangeListener {

    private final Instrument instrument;
    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();

    private final TrackBook trackBook;
    /**
     * 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿。
     **/
    private final Map<Source, IOrderBook> multiSrcBooks;
    private AggregateOrderBook aggBook;

    public InstrumentMaker(Instrument instrument, TrackBook trackBook) {
        this.instrument = instrument;
        this.trackBook = trackBook;
        multiSrcBooks = new ConcurrentHashMap<>();
    }

    public void run() {

        while (!isQuit.get()) {
            try {
                IOrderBook book = depthQueue.take();

                if (book.getInstrument() == instrument.asLong()) {
                    if (book.getSourceValue() == Source.Mix.ordinal()) {
                        if (aggBook != null) {
                            aggBook.destroy();
                            aggBook = null;
                        }
                        aggBook = (AggregateOrderBook) book;
                    } else {
                        IOrderBook old = multiSrcBooks.put(Source.values()[(int) book.getSourceValue()], book);
                        if (old != null) {
                            old.destroy();
                            old = null;
                        }
                    }
                }

                long[] bbo = getBBO();
//                List<Long> lst = new ArrayList<>();
//                lst.add(System.currentTimeMillis());
//                lst.add(book.getSourceValue());
//                lst.add(bbo == null ? -1 : bbo[0]);
//                lst.add(bbo == null ? -1 : bbo[1]);
//                System.out.println(lst);
                
                Set<Long> worstBidOrders = trackBook.getOrdersBetween(Side.BUY, Long.MAX_VALUE, bbo[0]);
                Set<Long> worstAskOrders = trackBook.getOrdersBetween(Side.SELL, bbo[1], 0);
                

            } catch (InterruptedException ex) {

            } catch (Exception ex) {

            }

        }

    }

    /**
     * 获得最优出价和供应。（Best Bid and Offer),
     * 
     * @return
     */
    public long[] getBBO() {
        long floatDownward = 1000 - C.SPREAD_LOWLIMIT_MILLESIMAL / 2;
        long floatUpward = 1000 + C.SPREAD_LOWLIMIT_MILLESIMAL / 2;
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
        depthQueue.add(book);
    }

    @Override
    public void onTrade(Long instrument, TradeLog tradeLog) {

    }

}
