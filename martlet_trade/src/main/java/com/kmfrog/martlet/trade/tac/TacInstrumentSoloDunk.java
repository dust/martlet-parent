package com.kmfrog.martlet.trade.tac;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.exec.ExecTacHedgeOrder;

import io.broker.api.client.BrokerApiRestClient;

public class TacInstrumentSoloDunk extends InstrumentSoloDunk {

    BrokerApiRestClient client;

    public TacInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            BrokerApiRestClient client, int minSleepMillis, int maxSleepMillis) {
        super(instrument, src, trackBook, provider, minSleepMillis, maxSleepMillis);
        this.client = client;

    }

    @Override
    public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
        long ts = Math.max(book.getLastUpdateTs(), book.getLastReceivedTs());
        if (System.currentTimeMillis() - ts > C.SYMBOL_DELAY_MILLIS) {
            return;
        }

        provider.submitExec(new ExecTacHedgeOrder(instrument, price, spreadSize, client, trackBook));
    }

}
