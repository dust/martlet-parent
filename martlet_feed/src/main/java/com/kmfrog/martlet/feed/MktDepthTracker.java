package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;


public class MktDepthTracker extends Thread{
	
	final Logger logger = LoggerFactory.getLogger(MktDepthTracker.class);
	private BaseApiRestClient client;
	Source src;
	final Map<String, WsDataListener> listenersMap;
	final int sleepMillis;
	private AtomicBoolean isQuit = new AtomicBoolean(false);
	
	public MktDepthTracker(Source src, String[] symbolNames, WsDataListener[] listeners, BaseApiRestClient client, int sleepMillis) {
		this.src = src;
		this.listenersMap = new ConcurrentHashMap<>();
		this.client = client;
		this.sleepMillis = sleepMillis;
		for(int i=0; i<symbolNames.length; i++) {
			listenersMap.put(symbolNames[i], listeners[i]);
		}
	}
	
	@Override
	public void run() {
		while (!isQuit.get()) {
			try {
				Thread.sleep(sleepMillis);
				String[] symbols = new String[listenersMap.size()];
				listenersMap.keySet().toArray(symbols);
				for(String symbol: symbols) {
					JSONObject jsn = client.getDepth(symbol);
					if(listenersMap.containsKey(symbol)) {
						listenersMap.get(symbol).onJSON(jsn, true);
					}
				}
			}catch(Exception ex) {
				logger.warn(ex.getMessage(), ex);
			}
		}
	}

}
