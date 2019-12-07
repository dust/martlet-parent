package com.kmfrog.martlet.feed;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.impl.LoexDepthHandler;

public class RestDaemon extends Thread {
    
    private final Source source;
    private final LoexDepthHandler handler;
    private final Controller app;
    private final AtomicBoolean isQuit = new AtomicBoolean(false);
    private final Logger logger = LoggerFactory.getLogger(RestDaemon.class);

    public RestDaemon(Source src, LoexDepthHandler handler,
            Controller app) {
       
        super(String.format("%s-%s", src, RestDaemon.class.getSimpleName()));
        source = src;
        this.handler = handler;
        this.app = app;
    }

    @Override
    public void run() {
        try {
            while (!isQuit.get()) {
                try {
                    handler.reqDepth(app);
                    Thread.sleep(800L);
                } catch (InterruptedException ex) {
                    isQuit.compareAndSet(false, true);
                    logger.warn(ex.getMessage(), ex);
                } catch (Exception ex) {
                    logger.warn(ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

}
