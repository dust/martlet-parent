package com.kmfrog.martlet.feed.impl;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.BaseInstrumentDepth;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class BioneInstrumentDepth extends BaseInstrumentDepth{
	
	private final Lock lock;

	public BioneInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
		super(instrument, book, source, controller);
		lock = new ReentrantLock();
	}

	@Override
	public Object onSnapshot(String snap) {
		DefaultJSONParser parser = new DefaultJSONParser(snap);
		
		JSONObject root = parser.parseObject();
		JSONObject data = root.getJSONObject("data");
		long t = data.getLongValue("timestamp");
		
		lock.lock();
		try {
			if(t > lastTimestamp.get()) {
				book.clear(Side.BUY, source.ordinal());
				book.clear(Side.SELL, source.ordinal());
				JSONArray bids = data.getJSONArray("bids");
                JSONArray asks = data.getJSONArray("asks");
                updatePriceLevel(Side.BUY, bids);
                updatePriceLevel(Side.SELL, asks);
                
                lastTimestamp.set(t);
                book.setLastUpdateTs(lastTimestamp.get());
                controller.resetBook(source, instrument, book);
			}
		}catch(Exception ex) {
			ex.printStackTrace();
		}finally {
			lock.unlock();
		}

        return null;
	}
}
