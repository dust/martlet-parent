package com.kmfrog.martlet.feed.impl;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.BaseInstrumentDepth;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class BhexInstrumentDepth extends BaseInstrumentDepth {
    
    private final AtomicLong lastUpdateId;
    private final Lock lock;

    public BhexInstrumentDepth(Instrument instrument, IOrderBook book, Source source,
            Controller controller) {
        super(instrument, book, source, controller);
        
        lastUpdateId = new AtomicLong(0L);
        lock = new ReentrantLock();
        
    }

    @Override
    public void onMessage(String msg) {
        // have not used.
    }

    @Override
    public void onJSON(JSONObject root, boolean isSnapshot) {
        if(root.containsKey("data")) {
            JSONArray data = root.getJSONArray("data");
            JSONObject main = data.getJSONObject(0);
//            boolean isSnapshot = root.getBooleanValue("f");
            
            long t = main.getLongValue("t");
            long id = Long.valueOf(main.getString("v").replace("_", ""));
            JSONArray bids = main.getJSONArray("b");
            JSONArray asks = main.getJSONArray("a");
            
            lock.lock();
            try {
                if (id > lastUpdateId.get()) {
                    
//                    if(isSnapshot) {
                        book.clear(Side.BUY, source.ordinal());
                        book.clear(Side.SELL, source.ordinal());
//                    }
                    
                    updatePriceLevel(Side.BUY, bids);
                    updatePriceLevel(Side.SELL, asks);
                    // logger.info("onMessage. {}|{}|{}, {}", lastUpdateId.get(), evtFirstId, evtLastId, lastId);
                    lastUpdateId.set(id);
                    lastTimestamp.set(t);
                    book.setLastUpdateTs(t);
                }
            }
            finally {
                lock.unlock();
            }
            
        }
    }

    @Override
    public void onReset(int errCode, String reason) {

    }

    @Override
    public Object onSnapshot(String snap) {
        // TODO Auto-generated method stub
        return null;
    }

}
