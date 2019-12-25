package com.kmfrog.martlet.trade.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.util.Fmt;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.NewOrder;
import io.broker.api.client.domain.account.NewOrderResponse;
import io.broker.api.client.domain.account.TimeInForce;

public class TacPlaceOrderExec extends Exec {

    static Logger logger = LoggerFactory.getLogger(TacPlaceOrderExec.class);

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
        try {
            String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits(), instrument.getShowPriceFractionDigits());
            String quantityStr = Fmt.fmtNum(volume, instrument.getSizeFractionDigits());
            NewOrder order;
            if (side == Side.BUY) {
                order = NewOrder.limitBuy(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
            } else {
                order = NewOrder.limitSell(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
            }

//            order = order.quantity("1");
            logger.info("order:{}", order);
            NewOrderResponse resp = client.newOrder(order);
            logger.info("resp:{}", order.toString(), resp.toString());
            Long orderId = resp.getOrderId();
            String clientOrderId = resp.getClientOrderId();
            if (orderId != null && orderId.longValue() > 0) {
                trackBook.entry(orderId, side, price, volume, 0, clientOrderId);
            }
        } catch (Exception ex) {
            logger.warn("{}-{}@{}:{}", instrument.asString(), volume, price, ex.getMessage());
            ex.printStackTrace();
        }

    }

}
