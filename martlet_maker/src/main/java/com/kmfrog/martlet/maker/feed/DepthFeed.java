package com.kmfrog.martlet.maker.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.maker.core.InstrumentMaker;
import com.kmfrog.martlet.util.FeedUtils;

/**
 * 
 * @author dust Oct 28, 2019
 *
 */
public class DepthFeed extends Thread {

    final Logger logger = LoggerFactory.getLogger(DepthFeed.class);

    final ZMQ.Context ctx;
    final String address;
    ZMQ.Socket subscriber;
    AtomicBoolean isQuit;
    Map<Long, DataChangeListener> listeners;

    public DepthFeed(String host, int port, int threads) {
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
                    IOrderBook book = FeedUtils.parseZMQDepth(str);
                    if (book != null) {
                        Long instrument = book.getInstrument();
                        if (listeners.containsKey(instrument)) {
                            listeners.get(instrument).onDepth(instrument, book);
                        }
                    }

                } catch (ZMQException zex) {
                    logger.warn("");
                }

            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    public void register(Instrument btcusdt, InstrumentMaker im) {
        listeners.put(btcusdt.asLong(), im);
    }

    public void unregister(Long lngInstrument) {
        listeners.remove(lngInstrument);
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
