package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

public class InstrumentTrade extends Thread implements DataChangeListener {

    private final Instrument instrument;
    private final AtomicBoolean isQuit = new AtomicBoolean(false);
    private final BlockingQueue<TradeLog> depthQueue = new PriorityBlockingQueue<>();
    private final Map<Source, RollingTimeSpan<TradeLog>> avgTradeMap;

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
                TradeLog book = depthQueue.take();
                Source src = book.getSource();
                if (avgTradeMap.containsKey(src)) {
                    avgTradeMap.get(src).add(book);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onDepth(Long instrument, IOrderBook book) {

    }

    @Override
    public void onTrade(Long instrument, TradeLog tradeLog) {
        try {
            depthQueue.put(tradeLog);
        } catch (Exception ex) {

        }
    }

}
