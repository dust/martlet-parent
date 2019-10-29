package com.kmfrog.martlet.feed;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;

/**
 * 币对交易深度
 * @author dust Oct 14, 2019
 *
 */
public abstract class BaseInstrumentDepth implements WsDataListener, SnapshotDataListener {

    protected Logger logger = LoggerFactory.getLogger(getClass());
    protected final IOrderBook book;
    protected final Instrument instrument;
    protected final Source source;
    protected final Controller controller;
    
    /**
     * 深度最后更新时间。
     */
    protected final AtomicLong lastTimestamp;

    public BaseInstrumentDepth(Instrument instrument, IOrderBook book, Source source,
            Controller controller) {
        this.instrument = instrument;
        this.book = book;
        this.source = source;
        this.controller = controller;
        
        lastTimestamp = new AtomicLong(0L);

    }
    
    protected void updatePriceLevel(Side side, JSONArray priceLevels) {
        for (Object priceLevel : priceLevels) {
            JSONArray pair = (JSONArray) priceLevel;
            long price = pair.getBigDecimal(0).multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
            long size = pair.getBigDecimal(1).multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
            book.replace(side, price, size, source.ordinal());
        }
    }
    
    protected void incrPriceLevel(Side side, JSONArray priceLevels) {
        for (Object priceLevel : priceLevels) {
            JSONArray pair = (JSONArray) priceLevel;
            long price = pair.getBigDecimal(0).multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
            long size = pair.getBigDecimal(1).multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
            book.incr(side, price, size, source.ordinal());
        }
    }

    @Override
    public void onMessage(String msg) {

    }

    @Override
    public void onJSON(JSONObject json, boolean isSnapshot) {

    }

    @Override
    public void onReset(int errCode, String reason) {

    }

    @Override
    public Object onSnapshot(String msg){
        return null;
    }


}
