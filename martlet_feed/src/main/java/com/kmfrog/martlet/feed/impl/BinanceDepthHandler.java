package com.kmfrog.martlet.feed.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.websocket.api.Session;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.feed.BaseWebSocketHandler;
import com.kmfrog.martlet.feed.WsDataListener;

public class BinanceDepthHandler extends BaseWebSocketHandler {

    String wsUrlFmt;
    Map<String, WsDataListener> listenersMap;


    public BinanceDepthHandler(String wsUrlFmt, String[] symbols, WsDataListener[] listeners) {
        super();
        this.wsUrlFmt = wsUrlFmt;
        listenersMap = new ConcurrentHashMap<>();
        symbolNames = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < symbols.length; i++) {
            symbolNames.add(symbols[i]);
            listenersMap.put(String.format("%s@depth", symbols[i]), listeners[i]);
        }
    }
    
    @Override
    public String getWebSocketUrl() {
        return String.format(wsUrlFmt, String.join("@depth/", symbolNames));
    }

    @Override
    public void onMessage(Session sess, String msg) {
        DefaultJSONParser parser = new DefaultJSONParser(msg);
//         System.out.println("\n################\n");
//         System.out.println(msg);
//         System.out.println("\n################\n");
        try {
            JSONObject root = parser.parseObject();
            String symbolName = root.getString("stream");
            if (listenersMap.containsKey(symbolName)) {
                listenersMap.get(symbolName).onJSON(root.getJSONObject("data"), false);
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        } finally {
            parser.close();
        }
    }

}
