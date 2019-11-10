package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.InstrumentTrade;
import com.kmfrog.martlet.util.FeedUtils;

/**
 * 
 * @author dust Oct 28, 2019
 *
 */
public class TradeFeed extends Thread {

    final Logger logger = LoggerFactory.getLogger(TradeFeed.class);

    final ZMQ.Context ctx;
    final String address;
    ZMQ.Socket subscriber;
    AtomicBoolean isQuit;
    Map<Long, DataChangeListener> listeners;

    public TradeFeed(String host, int port, int threads) {
        isQuit = new AtomicBoolean(false);
        listeners = new ConcurrentHashMap<>();
        ctx = ZMQ.context(threads);
        address = String.format("tcp://%s:%d", host, port);

    }

    public void run() {
        try {
            subscriber = ctx.socket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

            while (!isQuit.get()) {
                try {
                    String str = subscriber.recvStr();
                    TradeLog log = FeedUtils.parseZMQTrade(str);
                    if (log != null) {
                        Long instrument = log.getInstrument();
                        if (listeners.containsKey(instrument)) {
                            listeners.get(instrument).onTrade(instrument, log);
                        }
                    }

                } catch (ZMQException ex) {
                    logger.warn(ex.getMessage(), ex);
                }

            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    public void register(Instrument instrument, DataChangeListener listener) {
        listeners.put(instrument.asLong(), listener);
    }
    
    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }
    
    public void destroy() {
        subscriber.close();
        ctx.close();
    }

}
