package com.kmfrog.martlet.trade;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.midi.Instrument;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.domain.TradeLog;

public class InstrumentMaker implements Runnable, DataChangeListener {

    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();
    // private BlockingQueue<Object>

    public InstrumentMaker(Instrument instrument) {

    }

    public void run() {

        while (!isQuit.get()) {
            try {
                IOrderBook book = depthQueue.take();
                
            } catch (InterruptedException ex) {

            } catch (Exception ex) {

            }

        }

    }

    @Override
    public void onDepth(Instrument instrument, IOrderBook book) {
        depthQueue.add(book);
    }

    @Override
    public void onTrade(Instrument instrument, TradeLog tradeLog) {

    }

}
