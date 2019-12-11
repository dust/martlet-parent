package com.kmfrog.martlet.maker.core;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.maker.exec.TradeLogExec;
import com.kmfrog.martlet.maker.feed.DataChangeListener;

public class InstrumentTrade extends Thread implements DataChangeListener {

    private final Instrument instrument;
    private final AtomicBoolean isQuit = new AtomicBoolean(false);
    private final BlockingQueue<TradeLog> queue = new PriorityBlockingQueue<>(); // 某个币对的实时成交数据。
    private final Provider provider;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public InstrumentTrade(Instrument instrument, Provider provider) {
        super(String.format("%s-%s-%d", InstrumentTrade.class.getSimpleName(), instrument.asString(),
                instrument.asLong()));
        this.instrument = instrument;
        this.provider = provider;
    }

    public void run() {
        while (!isQuit.get()) {
            try {
                TradeLog tradeLog = queue.take();
                Source src = tradeLog.getSource();
                RollingTimeSpan<TradeLog> avgLogs = provider.getAvgTrade(src, instrument);
                if (avgLogs != null) {
                    avgLogs.add(tradeLog);
                }
//                Source preferSource = provider.getPreferSource();
//                if (preferSource != null && src == preferSource) {
                    provider.submit(new TradeLogExec(tradeLog, src, instrument, provider, logger));
//                }
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
            logger.warn(ex.getMessage(), ex.getMessage());
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
