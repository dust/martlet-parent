package com.kmfrog.martlet.trade.bione;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.bione.BioneApiRestClient;
import com.kmfrog.martlet.trade.Provider;

public class BioneOpenOrderTracker extends Thread {
	
	final Logger logger = LoggerFactory.getLogger(BioneOpenOrderTracker.class);
	private String[] instruments;
	private Provider provider;
	private BioneApiRestClient client;
	private AtomicBoolean isQuit = new AtomicBoolean(false);

	public BioneOpenOrderTracker(String[] instruments, BioneApiRestClient client, Provider provider) {
		super();
		this.instruments = instruments;
		this.client = client;
		this.provider = provider;
	}
	
	public void run() {
		while(!isQuit.get()) {
			try {
				Thread.sleep(5000);
				for(String instrument : instruments) {
					JSONObject root = client.getDepth(instrument);
					JSONObject data = root.getJSONObject("data");
					JSONArray asks = data.getJSONArray("asks");
					JSONArray bids = data.getJSONArray("bids");
					JSONArray ask1 = asks.getJSONArray(0);
					JSONArray bid1 = bids.getJSONArray(0);
					
					/**
					 * ask1 bid1间隔太小(没有盘口空间了),则清理ask5 到 bid5之间的挂单
					 */
					if(ask1.getDoubleValue(0) - bid1.getDoubleValue(0) < 0.000001d) {
						double askPX = asks.getJSONArray(4).getDoubleValue(0);
						double bidPX = bids.getJSONArray(4).getDoubleValue(0);
						JSONObject ret = client.getOrders(instrument, 1, 1, 500);
						JSONArray arry = ret.getJSONArray("data");
				    	try {
					    	for(int i=0; i<arry.size(); i++) {
					    		JSONObject jb = arry.getJSONObject(i);
					    		double num = jb.getDouble("num");
					    		double price = jb.getDoubleValue("price");
					    		if(num < 2500 && price >= bidPX && price <= askPX) {
					    			client.cancelOrder(instrument, jb.getString("id"));
					    			Thread.sleep(1000);
					    		}
					    	}
				    	} catch(Exception ex) {
				    		ex.printStackTrace();
				    	} finally {
							
						}
					}
				}
			}catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
	}
	
	public void quit() {
		isQuit.compareAndSet(false, true);
		interrupt();
	}
}
