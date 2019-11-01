package com.kmfrog.martlet.feed.impl;

import java.math.BigDecimal;

import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.BaseInstrumentTrade;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class BinanceInstrumentTrade extends BaseInstrumentTrade {

    public BinanceInstrumentTrade(Instrument instrument, Controller app) {
        super(instrument, Source.Binance, app);
    }

    @Override
    public void onJSON(JSONObject root, boolean isSnapshot) {
        long id = root.getLongValue("a");
        long price = root.getBigDecimal("p").multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
        long volume = root.getBigDecimal("q").multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
        long cnt = root.getLongValue("l") - root.getLongValue("f");
        boolean isBuy = root.getBoolean("m");
        long ts = root.getLongValue("T");
        if(ts > lastTimestamp.get()) {
            lastTimestamp.set(ts);
        }
        lastReceived.set(System.currentTimeMillis());
        controller.logTrade(source, instrument, id, price, volume, cnt, isBuy, ts, lastReceived.get());
        
    }
    
    

}
