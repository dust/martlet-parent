package com.kmfrog.martlet.feed.impl;

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

public class LoexInstrumentDepth extends BaseInstrumentDepth{
	
	private final Lock lock;

	public LoexInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
		super(instrument, book, source, controller);

		lock = new ReentrantLock();
	}
	
	@Override
    public void onJSON(JSONObject root, boolean isSnapshot) {
		JSONObject data = root.getJSONObject("data");
		if(!data.containsKey("tick")) {
			return;
		}
		
		lock.lock();
		try {
			JSONObject main = root.getJSONObject("tick");
			JSONArray bids = main.getJSONArray("bids");
			JSONArray asks = main.getJSONArray("asks");
			book.clear(Side.BUY, source.ordinal());
			book.clear(Side.SELL, source.ordinal());
			updatePriceLevel(Side.BUY, bids);
			updatePriceLevel(Side.SELL, asks);
			
			lastTimestamp.set(System.currentTimeMillis());
			book.setLastUpdateTs(lastTimestamp.get());
			controller.resetBook(source, instrument, book);
		} catch(Exception ex) {
			ex.printStackTrace();
		} finally {
			lock.unlock();
		}
        
    }

}
