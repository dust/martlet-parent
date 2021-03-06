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

public class HuobiTradeHandler extends BaseWebSocketHandler {

    private final String wsUrl;  //WS_URL = "wss://api.huobi.pro/ws";
    private final String tradeFmt; //CH_NAME_FMT = "market.%s.trade.detail";
    private Map<String, WsDataListener> listenersMap;
    private AtomicLong lastTs;

    public HuobiTradeHandler(String wsUrl, String tradeFmt, String[] symbols, WsDataListener[] listeners) {
        super();
        this.wsUrl = wsUrl;
        this.tradeFmt = tradeFmt;
        lastTs = new AtomicLong(0L);
        listenersMap = new ConcurrentHashMap<>();
        symbolNames = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < symbols.length; i++) {
            symbolNames.add(symbols[i]);
            listenersMap.put(String.format(tradeFmt, symbols[i]), listeners[i]);
        }
    }

    @Override
    public String getWebSocketUrl() {
        return wsUrl;
    }

    @Override
    public void onConnect(Session session) {
        super.onConnect(session);
        try {
            final String subFmt = "{\"sub\": \""+tradeFmt+"\", \"id\": \"%d\"}";
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
    public void subscribeSymbol(String symbol, WsDataListener baseDepth) {
        try {
            symbolNames.add(symbol);
            listenersMap.put(symbol, baseDepth);
            final String subFmt = "{\"sub\": \"" + tradeFmt + "\", \"id\": \"%d\"}";
            session.getRemote().sendString(String.format(subFmt, symbol, generateReqId()));
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    @Override
    public void unsubscribeSymbol(String symbol) {
        try {
            final String subFmt = "{\"unsub\": \"" + tradeFmt + "\", \"id\": \"%d\"}";
            session.getRemote().sendString(String.format(subFmt, symbol, generateReqId()));
            listenersMap.remove(symbol);
            symbolNames.remove(symbol);
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
                if (logger.isInfoEnabled()) {
                    logger.info(" eorror procotol, There is no 'ts' {}", msg);
                }
                return;
            }

            long ts = root.getLongValue("ts");
            
            if (!root.containsKey("ch")) {
                if (root.containsKey("status") || root.containsKey("subbed")) {
                    // response, pass directly.
                    return;
                }
                if (logger.isInfoEnabled()) {
                    logger.info(" eorror procotol,  There is no 'ch': {}", msg);
                }
                return;
            }
            // 实时交易流水日志，允许时序错误，但不更新最后接收时间。
            if (ts > lastTs.get()) {
                lastTs.set(ts);
            }

            String channelName = root.getString("ch");
            if (listenersMap.containsKey(channelName)) {
                listenersMap.get(channelName).onJSON(root.getJSONObject("tick"), false);
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        } finally {
            parser.close();
        }
    }

}
