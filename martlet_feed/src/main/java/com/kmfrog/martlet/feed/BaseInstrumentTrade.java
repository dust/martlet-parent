package com.kmfrog.martlet.feed;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;

public abstract class BaseInstrumentTrade implements WsDataListener {
    
    protected Logger logger = LoggerFactory.getLogger(getClass());

    protected final Instrument instrument;
    protected final Source source;
    protected final Controller controller;
    
    
    /**
     * 深度最后更新时间。
     */
    protected final AtomicLong lastTimestamp;
    
    /**
     * 最后接收时间。
     */
    protected final AtomicLong lastReceived;
    
    public BaseInstrumentTrade(Instrument instrument, Source source, Controller app) {
        this.instrument = instrument;
        this.source = source;
        this.controller = app;
        
        lastTimestamp = new AtomicLong(0L);
        lastReceived = new AtomicLong(0L);
    }

    @Override
    public void onMessage(String msg) {
        
    }

   

    @Override
    public void onReset(int errCode, String reason) {
       
    }

    @Override
    public void onJSON(JSONObject json, boolean isSnapshot) {
       
        
    }

}
