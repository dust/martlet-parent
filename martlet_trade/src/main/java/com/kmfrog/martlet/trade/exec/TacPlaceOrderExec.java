package com.kmfrog.martlet.trade.exec;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.util.Fmt;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.NewOrder;
import io.broker.api.client.domain.account.NewOrderResponse;
import io.broker.api.client.domain.account.TimeInForce;

public class TacPlaceOrderExec extends Exec {

    Instrument instrument;
    long price;
    long volume;
    Side side;
    BrokerApiRestClient client;
    TrackBook trackBook;

    /**
     * 
     * @param instrument
     * @param price
     * @param volume
     * @param side
     * @param client
     * @param trackBook
     */
    public TacPlaceOrderExec(Instrument instrument, long price, long volume, Side side, BrokerApiRestClient client,
            TrackBook trackBook) {
        super(System.currentTimeMillis());
        this.instrument = instrument;
        this.price = price;
        this.volume = volume;
        this.side = side;
        this.client = client;
        this.trackBook = trackBook;
    }

    @Override
    public void run() {
        String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits());
        String quantityStr = Fmt.fmtNum(volume, instrument.getSizeFractionDigits());
        NewOrder order;
        if (side == Side.BUY) {
            order = NewOrder.limitBuy(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        } else {
            order = NewOrder.limitSell(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        }

        NewOrderResponse resp = client.newOrder(order);
        Long orderId = resp.getOrderId();
        if (orderId != null && orderId.longValue() > 0) {
            trackBook.entry(orderId, side, price, volume);
        }

    }

}