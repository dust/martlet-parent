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

/**
 * @Author dust
 */
public class OkexInstrumentDepth extends BaseInstrumentDepth {

    // private final int MAX_DEPTH = 100;
    // private final AtomicLong lastChecksum;
    private ReentrantLock lock = new ReentrantLock();

    public OkexInstrumentDepth(Instrument instrument, IOrderBook book, Source source, Controller controller) {
        super(instrument, book, source, controller);
    }

    @Override
    public void onJSON(JSONObject root, boolean isSnapshot) {

        long ts = root.getDate("timestamp").getTime();
        // long checksum = root.getLongValue("checksum");

        lock.lock();
        try {
            if (ts < lastTimestamp.get() || (lastTimestamp.get()==0 && !isSnapshot)) {
                // 过期的推送或第一个推送并且不是快照数据（全量）。跳过。
                return;
            }
            
            JSONArray bids = root.getJSONArray("bids");
            JSONArray asks = root.getJSONArray("asks");

            if (isSnapshot) {
                int src = source.ordinal();
                book.clear(Side.BUY, src);
                book.clear(Side.SELL, src);
            }

            updatePriceLevel(Side.BUY, bids);
            updatePriceLevel(Side.SELL, asks);
            // lastChecksum.set(checksum);
            lastTimestamp.set(ts);
            book.setLastUpdateTs(ts);
            
            //因为order book（在更新后）总是一个全量数据，所以每次都是重设。
            controller.resetBook(source, instrument, book);

        } finally {
            lock.unlock();
        }
        
        //检查数据是否包含简单错误。
        checkData();
    }

    private void checkData() {
        lock.lock();
        try {
            if (book.getBestAskPrice() <= book.getBestBidPrice()) {
                book.clear(Side.BUY, source.ordinal());
                book.clear(Side.SELL, source.ordinal());
                controller.reset(source, instrument, this, true, false);
                lastTimestamp.set(0L);
            }
        } finally {
            lock.unlock();
        }
    }

}
