package com.kmfrog.martlet.feed;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.net.FeedBroadcast;

public class TradePusher extends Thread {

    private final AtomicBoolean isQuit;
    private final Controller app;
    private final BlockingQueue<TradeLog> queue;
    private final FeedBroadcast broadcast;

    protected final AtomicLong times = new AtomicLong(0L);
    protected final AtomicLong tt = new AtomicLong(0L);

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public TradePusher(FeedBroadcast broadcast, Controller app) {
        this.app = app;
        this.broadcast = broadcast;
        isQuit = new AtomicBoolean(false);
        queue = new PriorityBlockingQueue<>();
    }

    @Override
    public void run() {
        while (!isQuit.get()) {
            try {
                TradeLog tradeLog = queue.take();
                long start = 0;
                if (BaseWebSocketHandler.DBG) {
                    start = System.currentTimeMillis();
                }
                
//                if(!)
                
                broadcast.sendTadeLog(tradeLog.toLongArray());

                if (BaseWebSocketHandler.DBG) {
                    tt.addAndGet(System.currentTimeMillis() - start);
                    times.incrementAndGet();
                }
                
                
            } catch (InterruptedException e) {
                logger.warn("{}({}), {}", e.getMessage());
            } catch (Exception ex) {
                logger.error(ex.getMessage(), ex);
            }
        }

    }

    public void put(Source src, Instrument instrument, long id, long price, long volume, long cnt, boolean isBuy,
                    long ts, long recvTs){
        TradeLog log = new TradeLog(src, instrument.asLong(), id, price, volume, cnt, isBuy, ts, recvTs);
        try {
            queue.put(log);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    void dumpStats(PrintStream ps) {
        ps.format("\n TradePusher, %d|%d\n", tt.get(), times.get());
    }
    
    private boolean checkDeviate(long[] tradeLog) {
        
        return true;
    }

}
