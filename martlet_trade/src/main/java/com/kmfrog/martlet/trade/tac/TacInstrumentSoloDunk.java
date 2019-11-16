package com.kmfrog.martlet.trade.tac;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.util.Fmt;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.NewOrder;
import io.broker.api.client.domain.account.NewOrderResponse;
import io.broker.api.client.domain.account.TimeInForce;

public class TacInstrumentSoloDunk extends InstrumentSoloDunk {

    RollingTimeSpan<TradeLog> boughtLogs;
    RollingTimeSpan<TradeLog> soldLogs;
    BrokerApiRestClient client;

    public TacInstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            BrokerApiRestClient client, int minSleepMillis, int maxSleepMillis) {
        super(instrument, src, trackBook, provider, minSleepMillis, maxSleepMillis);
        boughtLogs = new RollingTimeSpan<>(2 * 60000);
        soldLogs = new RollingTimeSpan<>(2 * 60000);

    }

    @Override
    public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
        long ts = Math.max(book.getLastUpdateTs(), book.getLastReceivedTs());
        if (System.currentTimeMillis() - ts > C.SYMBOL_DELAY_MILLIS) {
            return;
        }

        // the spread greater than 5 price unit
        if (spreadSize > C.SPREAD_PRICE_UNIT_SIMPLE_LIMIT) {
            // simple solo dunk
            placeHedgeOrder(price, spreadSize, false);
        } else {
            // sell after buy.
            placeHedgeOrder(price, spreadSize, true);
        }

    }

    private void placeHedgeOrder(long price, long spreadSize, boolean buyAfterSell) {
        boolean sellFirst = buyAfterSell ? true : System.currentTimeMillis() % 100 <= C.SELL_FIRST_RATIO;
        String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits());
        String quantityStr = getQuantityStr();
        NewOrder buy = NewOrder.limitBuy(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        NewOrder sell = NewOrder.limitSell(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        if (sellFirst) {
            NewOrderResponse resp = client.newOrder(sell);
        }
    }

    private String getQuantityStr() {
        return null;
    }

}
