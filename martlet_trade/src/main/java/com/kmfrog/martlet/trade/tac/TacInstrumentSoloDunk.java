package com.kmfrog.martlet.trade.tac;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacHedgeOrderExec;

import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class TacInstrumentSoloDunk extends InstrumentSoloDunk {

    BrokerApiRestClient client;

    public TacInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            BrokerApiRestClient client, Param param) {
        super(instrument, src, trackBook, provider, param);
        this.client = client;

    }

    @Override
    public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
        long ts = Math.max(book.getLastUpdateTs(), book.getLastReceivedTs());
        long now = System.currentTimeMillis();
//        System.out.println(now + "|" + ts + "|" + (now - ts));
        if (now - ts > C.SYMBOL_DELAY_MILLIS) {
            return;
        }
        int avgSleepMillis = (minSleepMillis + maxSleepMillis) / 2;
        provider.submitExec(new TacHedgeOrderExec(source, instrument, price, spreadSize, vMin, vMax, avgSleepMillis,
                client, trackBook, provider));
    }
    
    private boolean isDepthNormal(IOrderBook lastBook, Side side) {
    	long ask1 = lastBook.getBestAskPrice();
    	long bid1 = lastBook.getBestBidPrice();
    	
    	long ask1Size = lastBook.getAskSize(ask1);
    	long bid1Size = lastBook.getBidSize(bid1);
    	
    	
    	return false;
    }

}
