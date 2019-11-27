package com.kmfrog.martlet.feed.impl;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.websocket.api.Session;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.feed.BaseWebSocketHandler;
import com.kmfrog.martlet.feed.WsDataListener;

import ch.qos.logback.core.pattern.parser.Parser;

public class BikunDepthHandler extends BaseWebSocketHandler{
	
	final String wsUrl;
	final String depthFmt;
    final Map<String, WsDataListener> listenersMap;
    
    public BikunDepthHandler(String wsUrl, String depthFmt, String[] symbols, WsDataListener[] listeners) {
    	super();
    	this.wsUrl = wsUrl;
    	this.depthFmt = depthFmt;
    	this.listenersMap = new ConcurrentHashMap<>();
    	this.symbolNames = ConcurrentHashMap.newKeySet();
    	for (int i = 0; i < symbols.length; i++) {
            symbolNames.add(symbols[i]);
            listenersMap.put(symbols[i], listeners[i]);
        }
    }

	@Override
	public String getWebSocketUrl() {
		// TODO Auto-generated method stub
		return wsUrl;
	}
	
	@Override
	public void onConnect(Session session) {
      try {
//    	  symbolNames.iterator()
    	  Iterator it = symbolNames.iterator();
    	  while(it.hasNext()) {
    		  String symbolName = (String) it.next();
    		  String fmtStr = symbolName.equals("AICUSDT") ? "aic1usdt" : symbolName.toLowerCase();
    		  String msg = String.format(depthFmt, fmtStr);
//    	      System.out.println("##### bikun subscribe msg: "+ msg);
              session.getRemote().sendString(msg);
              logger.info("sub:{}", msg);
    	  }
      } catch (Exception ex) {
          logger.warn(ex.getMessage(), ex);
      }
	}

	@Override
	protected void onMessage(Session session, String msg) {
//		System.out.println(String.format("####### Bikun ws message: %s", msg));
		DefaultJSONParser parser = new DefaultJSONParser(msg);
		try {
			JSONObject root = parser.parseObject();
			if(!root.containsKey("tick")) {
				return;
			}
			String symbolName = parseSymbolName(root.getString("channel"));
			if (listenersMap.containsKey(symbolName)) {
	            // root.getJSONArray -\-> JSONObject, 所以传入了root
	            listenersMap.get(symbolName).onJSON(root, root.getJSONObject("tick").containsKey("asks"));
	        }
		} catch(Exception ex) {
			logger.warn(ex.getMessage(), ex);
		} finally {
			parser.close();
		}
	}
	
	private String parseSymbolName(String channel) {
		String symbol = channel.split("_")[1];
		String symbolName = symbol.equals("aic1usdt") ? "AICUSDT" : symbol.toUpperCase();
		return symbolName;
	}

}
