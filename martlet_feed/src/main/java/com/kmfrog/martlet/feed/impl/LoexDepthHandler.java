package com.kmfrog.martlet.feed.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.kmfrog.martlet.feed.Controller;
import com.kmfrog.martlet.feed.RestSnapshotRunnable;
import com.kmfrog.martlet.feed.SnapshotDataListener;

public class LoexDepthHandler {

    private String[] symbols;
    private Map<String, SnapshotDataListener> listenersMap;
    private AtomicLong lastTs = new AtomicLong(0L);

    public LoexDepthHandler(String[] symbols, SnapshotDataListener[] listeners) {
        super();
        this.symbols = symbols;
        listenersMap = new ConcurrentHashMap<>();
        for (int i = 0; i < symbols.length; i++) {
            listenersMap.put(symbols[i], listeners[i]);
        }
    }

    public void reqDepth(String depthUrlFmt, Controller app) {
        for (String symbol : symbols) {
            RestSnapshotRunnable r = new RestSnapshotRunnable(symbol, depthUrlFmt, "GET", null, null,
                    listenersMap.get(symbol));
            app.submitTask(r);
        }
    }

    public void onSnapshot(String symbol, String resp) {
        if (listenersMap.containsKey(symbol)) {
            listenersMap.get(symbol).onSnapshot(resp);
            lastTs.set(System.currentTimeMillis());
        }
    }

}
