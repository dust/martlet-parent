package com.kmfrog.martlet.trade.tac;

import java.util.Map;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.exec.TacHedgeOrderExec;

import io.broker.api.client.BrokerApiRestClient;

public class TacInstrumentSoloDunk extends InstrumentSoloDunk {

    BrokerApiRestClient client;

    public TacInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            BrokerApiRestClient client, Map<String, String> args) {
        super(instrument, src, trackBook, provider, Integer.valueOf(args.get("minSleepMillis")),
                Integer.valueOf(args.get("maxSleepMillis")));
        this.client = client;

    }

    @Override
    public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
        long ts = Math.max(book.getLastUpdateTs(), book.getLastReceivedTs());
        if (System.currentTimeMillis() - ts > C.SYMBOL_DELAY_MILLIS) {
            return;
        }

        provider.submitExec(new TacHedgeOrderExec(instrument, price, spreadSize, client, trackBook));
    }

}
