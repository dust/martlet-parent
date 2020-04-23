package com.kmfrog.martlet.trade.bione;

import java.util.concurrent.atomic.AtomicLong;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.bione.BioneApiRestClient;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.BioneHedgeOrderExec;

public class BioneInstrumentSoloDunk extends InstrumentSoloDunk{
	
	private final AtomicLong lastOrder;
	private BioneApiRestClient client;

	public BioneInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
			Param args, BioneApiRestClient client) {
		super(instrument, src, trackBook, provider, args);
		this.client = client;
		this.lastOrder = new AtomicLong(0);
	}

	@Override
	public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
		long ts = Math.max(book.getLastUpdateTs(), book.getLastReceivedTs());
        long now = System.currentTimeMillis();
        
        if (now - ts > C.SYMBOL_DELAY_MILLIS) {
            return;
        }
        
        int avgSleepMillis = (minSleepMillis + maxSleepMillis) / 2;
        if(now - lastOrder.get() > avgSleepMillis) {
        	provider.submitExec(new BioneHedgeOrderExec(source, instrument, price, spreadSize, vMin, vMax, avgSleepMillis,
                    client, trackBook, provider));
            lastOrder.set(now);
        }
	}

}
