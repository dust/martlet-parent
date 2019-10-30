package com.kmfrog.martlet.feed.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.websocket.api.Session;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.feed.BaseWebSocketHandler;
import com.kmfrog.martlet.feed.WsDataListener;

public class OkexTradeHandler extends BaseWebSocketHandler {

    private static final String WS_URL = "wss://real.okex.com:8443/ws/v3";
    private final Map<String, WsDataListener> listenersMap;

    public OkexTradeHandler(String[] symbols, WsDataListener[] listeners) {
        listenersMap = new ConcurrentHashMap<>();
        symbolNames = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < symbols.length; i++) {
            symbolNames.add(symbols[i]);
            listenersMap.put(symbols[i], listeners[i]);
        }

    }

    @Override
    public String getWebSocketUrl() {
        return WS_URL;
    }

    @Override
    public void onConnect(Session session) {
        super.onConnect(session);
        String symbolJoin = String.join("\", \"spot/trade:", symbolNames);
        String sub = String.format("{\"op\": \"subscribe\", \"args\": [\"spot/trade:%s\"]}", symbolJoin);
        // logger.info("sub:{}", sub);
        send(sub);
    }

    private void send(String msg) {
        try {
            if (logger.isInfoEnabled()) {
                logger.info(" send: {}", msg);
            }
            session.getRemote().sendString(msg);
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    protected void onMessage(Session session, String msg) {
        if ("pong".equals(msg)) {
            // ignore heartbeat.
            return;
        }
        DefaultJSONParser parser = new DefaultJSONParser(msg);
        // System.out.println("\n######"+Thread.currentThread().getName()+"##########\n");
        // System.out.println(msg);
        // System.out.println("\n################\n");
        try {
            JSONObject root = parser.parseObject();
            if (root.containsKey("table") && root.getString("table").equals("spot/trade")) {
                JSONArray data = root.getJSONArray("data");
                int size = data.size();
                for (int i = 0; i < size; i++) {
                    JSONObject ele = data.getJSONObject(i);
                    String symbol = ele.getString("instrument_id");
                    if (listenersMap.containsKey(symbol)) {
                        listenersMap.get(symbol).onJSON(ele, false);
                    }

                }
            } else if (root.containsKey("event") && root.getString("event").equals("subscribe")) {
                // response for subscribe.
                return;
            }

        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        } finally {
            parser.close();
        }

    }

    @Override
    protected void onBinaryMessage(Session session, InputStream is) throws IOException {
        String result = uncompress(is);
        // onMessage(session, result);
        if (DBG) {
            onMessageWithStats(session, result);
        } else {
            onMessage(session, result);
        }
    }

    @Override
    public Session keepAlive() {
        Session ret = super.keepAlive();
        ByteBuffer payload = ByteBuffer.wrap("ping".getBytes());
        try {
            if (ret != null) {
                ret.getRemote().sendPing(payload);
            }
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
        }
        return ret;
    }

}
