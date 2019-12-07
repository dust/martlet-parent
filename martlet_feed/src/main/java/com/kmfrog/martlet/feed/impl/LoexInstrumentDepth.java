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


public class LoexInstrumentDepth extends BaseInstrumentDepth {

    private final Lock lock;

    public LoexInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
        super(instrument, book, source, controller);
        lock = new ReentrantLock();
    }

    @Override
    public void onMessage(String msg) {
        // have not used.
    }

    @Override
    public void onJSON(JSONObject tick, boolean isSnapshot) {

        

        // JSONArray bids = tick.getJSONArray("buys");
        // JSONArray asks = tick.getJSONArray("asks");

        lock.lock();
        try {
            if (isSnapshot) {
                book.clear(Side.BUY, source.ordinal());
                book.clear(Side.SELL, source.ordinal());
                JSONArray bids = tick.getJSONArray("buys");
                JSONArray asks = tick.getJSONArray("asks");
                updatePriceLevel(Side.BUY, bids);
                updatePriceLevel(Side.SELL, asks);
            } /*else {

//                String side = main.getString("side");
//                Side updateSide = side.equals("asks") ? Side.SELL : Side.BUY;
//                JSONArray ticks = new JSONArray();
//                JSONArray tick = new JSONArray();
//                tick.add(main.getString("price"));
//                tick.add(main.getString("volume"));
//
//                updatePriceLevel(updateSide, ticks);
            }*/

            // logger.info("onMessage. {}|{}|{}, {}", lastUpdateId.get(), evtFirstId, evtLastId, lastId);
            lastTimestamp.set(System.currentTimeMillis());
            book.setLastUpdateTs(lastTimestamp.get());
            controller.resetBook(source, instrument, book);

        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public void onReset(int errCode, String reason) {

    }

    @Override
    public Object onSnapshot(String snap) {
        DefaultJSONParser parser = new DefaultJSONParser(snap);
        // System.out.println("\n################\n");
        // System.out.println(msg);
        // System.out.println("\n################\n");
        try {
            JSONObject root = parser.parseObject();
            if (root.containsKey("data")) {
                JSONObject data = root.getJSONObject("data");
                if (data.containsKey("tick")) {
                    JSONObject tick = data.getJSONObject("tick");
                    onJSON(tick, true);
                }
            }
        } finally {
            parser.close();
        }

        return null;
//=======
//public class LoexInstrumentDepth extends BaseInstrumentDepth{
//	
//	private final Lock lock;
//
//	public LoexInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
//		super(instrument, book, source, controller);
//
//		lock = new ReentrantLock();
//	}
//	
//	@Override
//    public void onJSON(JSONObject root, boolean isSnapshot) {
//		JSONObject data = root.getJSONObject("data");
//		if(!data.containsKey("tick")) {
//			return;
//		}
//		
//		lock.lock();
//		try {
//			JSONObject main = root.getJSONObject("tick");
//			JSONArray bids = main.getJSONArray("bids");
//			JSONArray asks = main.getJSONArray("asks");
//			book.clear(Side.BUY, source.ordinal());
//			book.clear(Side.SELL, source.ordinal());
//			updatePriceLevel(Side.BUY, bids);
//			updatePriceLevel(Side.SELL, asks);
//			
//			lastTimestamp.set(System.currentTimeMillis());
//			book.setLastUpdateTs(lastTimestamp.get());
//			controller.resetBook(source, instrument, book);
//		} catch(Exception ex) {
//			ex.printStackTrace();
//		} finally {
//			lock.unlock();
//		}
//        
//>>>>>>> 036b63179010f1c08cbe346121fb154568f3a0e0
    }

}
