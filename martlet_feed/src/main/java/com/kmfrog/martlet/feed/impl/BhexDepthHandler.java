package com.kmfrog.martlet.feed.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.websocket.api.Session;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.feed.BaseWebSocketHandler;
import com.kmfrog.martlet.feed.WsDataListener;

public class BhexDepthHandler extends BaseWebSocketHandler {

    final String wsUrl;
    final String depthFmt;
    final Map<String, WsDataListener> listenersMap;

    public BhexDepthHandler(String wsUrl, String depthFmt, String[] symbols, WsDataListener[] listeners) {
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
        return wsUrl;
    }

    @Override
    public void onConnect(Session session) {
        try {
//            String depth = "{ \"symbol\": \"%s\", \"topic\": \"depth\",  \"event\": \"sub\", \"params\": {\"binary\": false}}";
            String msg = String.format(depthFmt, StringUtils.join(symbolNames, C.SEPARATOR));
            session.getRemote().sendString(msg);
             logger.info("sub:{}", msg);
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }

    @Override
    protected void onMessage(Session sess, String msg) {
        DefaultJSONParser parser = new DefaultJSONParser(msg);
//        System.out.println("\n################\n");
//        System.out.println(msg);
//        System.out.println("\n################\n");
        try {
            JSONObject root = parser.parseObject();
            if (!root.containsKey("symbol")) {
//                if (root.containsKey("code")) {
                    logger.warn("err: {}", msg);
//                }
                // else if(root.containsKey("pong")) {
                //
                // }
                return;
            }
            String symbolName = root.getString("symbol");
            if (listenersMap.containsKey(symbolName)) {
                // root.getJSONArray -\-> JSONObject, 所以传入了root
                listenersMap.get(symbolName).onJSON(root, false);
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        } finally {
            parser.close();
        }

    }

    @Override
    public Session keepAlive() {
        Session ret = super.keepAlive();
        ByteBuffer payload = ByteBuffer.wrap(String.format("{\"ping\": %d", this.generateReqId()).getBytes());
        try {
            ret.getRemote().sendPing(payload);
        } catch (IOException e) {
            logger.warn(e.getMessage(), e);
        }
        return ret;
    }

}
