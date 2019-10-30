package com.kmfrog.martlet.feed.impl;

import java.math.BigDecimal;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.BaseInstrumentTrade;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class OkexInstrumentTrade extends BaseInstrumentTrade {

    public OkexInstrumentTrade(Instrument instrument, Source source, Controller app) {
        super(instrument, source, app);
    }
    
    public void onJSON(JSONObject obj, boolean isSnapshot) {
        
            long id = obj.getLongValue("trade_id");
            long price = obj.getBigDecimal("price").multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
            long volume = obj.getBigDecimal("size").multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
            long cnt = 0;
            long isBuy = "buy".equals(obj.getString("side")) ? 1 : 0;
            long ts = obj.getLongValue("timestamp");
            controller.logTrade(source, instrument, id, price, volume, cnt, isBuy, ts);
        
    }

}
