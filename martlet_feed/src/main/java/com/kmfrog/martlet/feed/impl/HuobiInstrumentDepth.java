package com.kmfrog.martlet.feed.impl;

import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.BaseInstrumentDepth;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class HuobiInstrumentDepth extends BaseInstrumentDepth {

    // private final int MAX_DEPTH = 100;
    private ReentrantLock lock = new ReentrantLock();

    public HuobiInstrumentDepth(Instrument instrument, IOrderBook book, Source source,
            Controller controller) {
        super(instrument, book, source, controller);
    }

    @Override
    public void onMessage(String msg) {
        // not used.
    }

    @Override
    public void onJSON(JSONObject json, boolean isSnapshot) {
        lock.lock();
        try {
            long ts = json.getLongValue("ts");
            if (ts < lastTimestamp.get()) {
                return;
            }

            lastTimestamp.set(ts);
            JSONArray bids = json.getJSONArray("bids");
            JSONArray asks = json.getJSONArray("asks");

            book.clear(Side.BUY, source.ordinal());
            book.clear(Side.SELL, source.ordinal());

            updatePriceLevel(Side.BUY, bids);
            updatePriceLevel(Side.SELL, asks);
            book.setLastUpdateTs(ts);
            
            controller.resetBook(source, instrument, book);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void onReset(int errCode, String reason) {
        // TODO Auto-generated method stub

    }

    @Override
    public Object onSnapshot(String snap) {
        // TODO Auto-generated method stub
        return null;
    }

}
