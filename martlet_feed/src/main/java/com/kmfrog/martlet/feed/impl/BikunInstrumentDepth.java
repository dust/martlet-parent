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

public class BikunInstrumentDepth extends BaseInstrumentDepth{
	
    private final Lock lock;

	public BikunInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
		super(instrument, book, source, controller);

        lock = new ReentrantLock();
	}

	@Override
    public void onMessage(String msg) {
        // have not used.
    }

    @Override
    public void onJSON(JSONObject root, boolean isSnapshot) {
        if(root.containsKey("tick")) {
        	JSONObject main = root.getJSONObject("tick");
        	
//        	JSONArray bids = tick.getJSONArray("buys");
//        	JSONArray asks = tick.getJSONArray("asks");
        	long t = root.getLongValue("ts");
        	
        	
            lock.lock();
            try {
                if (t > lastTimestamp.get()) {
                    if(isSnapshot) {
                        book.clear(Side.BUY, source.ordinal());
                        book.clear(Side.SELL, source.ordinal());
                        JSONArray bids = main.getJSONArray("buys");
                        JSONArray asks = main.getJSONArray("asks");
                        updatePriceLevel(Side.BUY, bids);
                        updatePriceLevel(Side.SELL, asks);
                    }else {
                    	
                    	String side = main.getString("side");
                    	Side updateSide = side.equals("asks") ? Side.SELL : Side.BUY;
                    	JSONArray ticks = new JSONArray();
                    	JSONArray tick = new JSONArray();
                    	tick.add(main.getString("price"));
                    	tick.add(main.getString("volume"));
                    	
                    	updatePriceLevel(updateSide, ticks);
                    }
                    
                    // logger.info("onMessage. {}|{}|{}, {}", lastUpdateId.get(), evtFirstId, evtLastId, lastId);
                    lastTimestamp.set(t);
                    book.setLastUpdateTs(lastTimestamp.get());
                    controller.resetBook(source, instrument, book);
                }
            } catch(Exception ex) {
            	ex.printStackTrace();
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
