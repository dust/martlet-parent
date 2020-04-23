package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BaseRestHandler {

	private Set<String> symbols;
    private final String depthUrlFmt;
    private Map<String, SnapshotDataListener> listenersMap;
    
    public BaseRestHandler(String depthUrlFmt, String[] symbolNames, SnapshotDataListener[] listeners) {
    	this.depthUrlFmt = depthUrlFmt;
    	this.listenersMap = new ConcurrentHashMap<>();
    	this.symbols = ConcurrentHashMap.newKeySet();
    	for(int i=0; i<symbolNames.length; i++) {
    		this.listenersMap.put(symbolNames[i], listeners[i]);
    		this.symbols.add(symbolNames[i]);
    	}
    }
    
    public void reqDepth(Controller app) {
        for (String symbol : symbols) {
        	String fmtSymbolString = formatSymbol(symbol);
            String url = String.format(depthUrlFmt, fmtSymbolString);
            RestSnapshotRunnable r = new RestSnapshotRunnable(url, "GET", null, null, listenersMap.get(symbol));
            app.submitTask(r);
        }
    }
    
    public Set<String> getSymbols() {
    	return this.symbols;
    }
    
    private String formatSymbol(String symbol) {
    	String[] keyCoins = {"BTC", "USDT", "ETH"};
    	String retString = symbol;
    	for(int i=0; i<keyCoins.length; i++) {
    		retString = formatSymbolByKeyCoin(symbol, keyCoins[i]);
    		if(retString.contains("_")) {
    			break;
    		}
    	}
    	return retString.toLowerCase();
    }
    
    private String formatSymbolByKeyCoin(String symbol, String keyCoin) {
    	if(symbol.startsWith(keyCoin)) {
    		return symbol.replace(keyCoin, keyCoin+"_");
    	}else if(symbol.endsWith(keyCoin)) {
    		return symbol.replace(keyCoin, "_"+keyCoin);
    	}
    	return symbol;
    }
}
