package com.kmfrog.martlet.trade.loex;

import java.util.concurrent.atomic.AtomicLong;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.BikunHedgeOrderExec;
import com.kmfrog.martlet.trade.exec.LoexHedgeOrderExec;
import com.kmfrog.martlet.feed.loex.LoexApiRestClient;


public class LoexInstrumentSoloDunk extends InstrumentSoloDunk{
	
	LoexApiRestClient client;
	private final AtomicLong lastOrder;

	public LoexInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
			Param args, LoexApiRestClient client) {
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
        	provider.submitExec(new LoexHedgeOrderExec(source, instrument, price, spreadSize, vMin, vMax, avgSleepMillis,
                    client, trackBook, provider));
            lastOrder.set(now);
        }
	}

}
