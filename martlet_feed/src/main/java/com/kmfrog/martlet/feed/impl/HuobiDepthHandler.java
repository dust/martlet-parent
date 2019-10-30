package com.kmfrog.martlet.feed.impl;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.websocket.api.Session;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.feed.BaseWebSocketHandler;
import com.kmfrog.martlet.feed.WsDataListener;

public class HuobiDepthHandler extends BaseWebSocketHandler {

    private static final String WS_URL = "wss://api.huobi.pro/ws";
    private static final String CH_NAME_FMT = "market.%s.depth.step0";
    private Map<String, WsDataListener> listenersMap;
    private AtomicLong lastTs;

    public HuobiDepthHandler(String[] symbols, WsDataListener[] listeners) {
        super();
        lastTs = new AtomicLong(0L);
        listenersMap = new ConcurrentHashMap<>();
        symbolNames = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < symbols.length; i++) {
            symbolNames.add(symbols[i]);
            listenersMap.put(String.format(CH_NAME_FMT, symbols[i]), listeners[i]);
        }
    }

    @Override
    public String getWebSocketUrl() {
        return WS_URL;
    }

    @Override
    public void onConnect(Session session) {
        super.onConnect(session);
        try {
            final String subFmt = "{\"sub\": \"market.%s.depth.step0\", \"id\": \"%d\"}";
            symbolNames.stream().forEach(symbol -> {
                try {
                    session.getRemote().sendString(String.format(subFmt, symbol, generateReqId()));
                } catch (IOException ex) {
                    logger.warn(ex.getMessage(), ex);
                }
            });

        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    @Override
    protected void onMessage(Session sess, String msg) {
        if (logger.isDebugEnabled()) {
            logger.debug("onMessage: {}", msg);
        }
        DefaultJSONParser parser = new DefaultJSONParser(msg);
        // System.out.println("\n################\n");
        // System.out.println(msg);
        // System.out.println("\n################\n");
        try {
            JSONObject root = parser.parseObject();
            if (!root.containsKey("ts")) {
                // ping
                if (root.containsKey("ping")) {
                    sess.getRemote().sendString(msg.replace("ping", "pong"));
                    return;
                }
                if(logger.isInfoEnabled()) {
                    logger.info(" eorror procotol, There is no 'ts' {}", msg);
                }
                return;
            }
            
            long ts = root.getLongValue("ts");            
            if (ts <= lastTs.get() || !root.containsKey("ch")) {
                if(root.containsKey("status") || root.containsKey("subbed")) {
                    //response, pass directly.
                    return;
                }
                if(logger.isInfoEnabled()) {
                    logger.info(" eorror procotol,  There is no 'ch': {}", msg);
                }
                return;
            }
            lastTs.set(ts);
            
            String channelName = root.getString("ch");
            if (listenersMap.containsKey(channelName)) {
                listenersMap.get(channelName).onJSON(root.getJSONObject("tick"), true);
            }
        }
        catch(Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
        finally {
            parser.close();
        }
    }

}
