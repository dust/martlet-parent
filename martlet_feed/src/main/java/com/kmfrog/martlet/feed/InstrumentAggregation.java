package com.kmfrog.martlet.feed;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.net.FeedBroadcast;

/**
 * 基于推送事件队列的聚合工作线程。
 *
 * @author dust Oct 23, 2019
 */
public class InstrumentAggregation extends Thread {

    private final AtomicBoolean isQuit;
    private final Instrument instrument;
    private final Controller app;
    private final BlockingQueue<AggregateRequest> queue;
    private final AggregateOrderBook aggBook;
    private final FeedBroadcast broadcast;

    protected final AtomicLong times = new AtomicLong(0L);
    protected final AtomicLong tt = new AtomicLong(0L);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public InstrumentAggregation(Instrument instrument, AggregateOrderBook book, FeedBroadcast broadcast,
            Controller app) {
        this.instrument = instrument;
        this.app = app;
        this.broadcast = broadcast;
        isQuit = new AtomicBoolean(false);
        queue = new PriorityBlockingQueue<>();
        aggBook = book;
    }

    @Override
    public void run() {

        while (!isQuit.get()) {
            try {
                AggregateRequest req = queue.take();
                long start = 0;
                if (BaseWebSocketHandler.DBG) {
                    start = System.currentTimeMillis();
                }

                // 先清理当前来源的订单项。
                int src = req.source.ordinal();
                aggBook.clear(Side.BUY, src);
                aggBook.clear(Side.SELL, src);

                // 检查偏离度。time/price level
                if (!checkDeviate(req.source, req.book)) {
                    continue;
                }

                // 全量更新当前来源的订单项。
                if (req.book != null) {
                    aggBook.setLastUpdateTs(req.book.getLastUpdateTs());
                    aggBook.aggregate(src, req.book);
                }

                broadcast.sendDepth(Source.Mix, aggBook, instrument.getPriceFractionDigits(),
                        instrument.getSizeFractionDigits(), 5);

                if (BaseWebSocketHandler.DBG) {
                    tt.addAndGet(System.currentTimeMillis() - start);
                    times.incrementAndGet();
                }
            } catch (InterruptedException e) {
                logger.warn("{}({}), {}", instrument.asString(), instrument.asLong(), e.getMessage());
            }

        }

    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    void dumpStats(PrintStream ps) {
        ps.format("\n Aggreate %s, %d|%d\n", instrument.asString(), tt.get(), times.get());
    }

    void putMsg(Source src, /* Action act, */ IOrderBook book) throws InterruptedException {
        AggregateRequest req = new AggregateRequest();
        // req.action = act;
        req.source = src;
        req.book = book;
        queue.put(req);
    }

    private boolean checkDeviate(Source src, IOrderBook book) {
        app.onDeviate(src, instrument, book, aggBook.getBestBidPrice(), aggBook.getBestAskPrice(),
                aggBook.getLastReceivedTs(), aggBook.getLastUpdateTs());
        return true;
    }

    static class AggregateRequest implements Comparable<AggregateRequest> {
        Source source;
        IOrderBook book;
        // Action action;

        @Override
        public int compareTo(AggregateRequest o) {
            if (o == this) {
                return 0;
            }
            if (o == null) {
                return 1;
            }

            if (book == null && o.book != null) {
                System.out.println("%%%%%");
                Thread.dumpStack();
                return -1;
            }

            if (book != null && o.book == null) {
                System.out.println("######");
                Thread.dumpStack();
                return 1;
            }
            if (book != null && o.book != null) {
                long diff = book.getLastUpdateTs() - o.book.getLastUpdateTs();
                if (diff != 0) {
                    return diff > 0 ? 1 : -1;
                }

                diff = book.getLastReceivedTs() - o.book.getLastReceivedTs();
                if (diff != 0) {
                    return diff > 0 ? 1 : -1;
                }
            }

            // 不会有等于的情况，因为不可能有更新时间（update timestamp)相同的推送。
            return source.ordinal() < o.source.ordinal() ? -1 : 1;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            // result = prime * result + (action == null ? 0 : action.hashCode());
            result = prime * result + ((book == null) ? 0 : book.hashCode());
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (this == obj) {
                return true;
            }

            if (!(obj instanceof AggregateRequest)) {
                return false;
            }

            AggregateRequest other = (AggregateRequest) obj;

            if (source != other.source) {
                return false;
            }
            // if (action != other.action) {
            // return false;
            // }
            if (book == null) {
                if (other.book != null) {
                    return false;
                }
            } else if (!book.equals(other.book)) {
                return false;
            }

            return true;
        }

    }

}
