package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

/**
 * 对交易平台推送的实时交易数据的处理器。
 * @author dust Nov 15, 2019
 *
 */
public class InstrumentTrade extends Thread implements DataChangeListener {

    private final Instrument instrument;
    private final AtomicBoolean isQuit = new AtomicBoolean(false);
    private final BlockingQueue<TradeLog> queue = new PriorityBlockingQueue<>();
    private final Map<Source, RollingTimeSpan<TradeLog>> avgTradeMap;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public InstrumentTrade(Instrument instrument, Source[] sources, RollingTimeSpan<TradeLog>[] avgTrades) {
        this.instrument = instrument;
        avgTradeMap = new ConcurrentHashMap<>();
        if (sources != null && avgTrades != null && sources.length == avgTrades.length) {
            for (int i = 0; i < sources.length; i++) {
                avgTradeMap.put(sources[i], avgTrades[i]);
            }
        }
    }

    public void run() {
        while (!isQuit.get()) {
            try {
                TradeLog tradeLog = queue.take();
                Source src = tradeLog.getSource();
                if (avgTradeMap.containsKey(src)) {
                    avgTradeMap.get(src).add(tradeLog);
                }
//                System.out.println(tradeLog);
            } catch (Exception ex) {
                logger.warn("{} - {} ", instrument.asString(), ex.getMessage());
            }
        }
    }

    @Override
    public void onDepth(Long instrument, IOrderBook book) {

    }

    @Override
    public void onTrade(Long instrument, TradeLog tradeLog) {
        try {
            queue.put(tradeLog);
        } catch (Exception ex) {

        }
    }
    
    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }
    
    public void destroy() {
        queue.clear();
    }

}
