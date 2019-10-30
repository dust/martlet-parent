package com.kmfrog.martlet.feed.impl;

import java.math.BigDecimal;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.BaseInstrumentTrade;
import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.Source;

public class HuobiInstrumentTrade extends BaseInstrumentTrade {
    
    

    public HuobiInstrumentTrade(Instrument instrument, Source source, Controller app) {
        super(instrument, source, app);
    }

    @Override
    public void onJSON(JSONObject json, boolean isSnapshot) {
        JSONArray data = json.getJSONArray("data");
        int size = data.size();
        for(int i=0; i<size; i++) {
            JSONObject obj = data.getJSONObject(i);
            long id = obj.getLongValue("tradeId");
            long price = obj.getBigDecimal("price").multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
            long volume = obj.getBigDecimal("amount").multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
            long cnt = 0;
            long isBuy = "buy".equals(obj.getString("direction")) ? 1 : 0;
            long ts = obj.getLongValue("ts");
            controller.logTrade(source, instrument, id, price, volume, cnt, isBuy, ts);
        }
    }
    
    

}
