package com.kmfrog.martlet.trade.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.util.Fmt;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.NewOrder;
import io.broker.api.client.domain.account.NewOrderResponse;
import io.broker.api.client.domain.account.TimeInForce;

/**
 * 
 * @author dust Nov 16, 2019
 *
 */
public class ExecTacHedgeOrder extends Exec {

    static Logger logger = LoggerFactory.getLogger(ExecTacHedgeOrder.class);

    BrokerApiRestClient client;
    Provider provider;
    long price;
    long spread;
    TrackBook trackBook;
    boolean isSellFirst;
    Instrument instrument;

    public ExecTacHedgeOrder(Instrument instrument, long price, long spread, BrokerApiRestClient client,
            TrackBook trackBook) {
        super(System.currentTimeMillis());
        this.instrument = instrument;
        this.price = price;
        this.spread = spread;
        this.client = client;
        this.trackBook = trackBook;

    }

    @Override
    public void run() {

        long quantity = getQuantity();
        String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits());
        String quantityStr = Fmt.fmtNum(quantity, instrument.getSizeFractionDigits());
        NewOrder buy = NewOrder.limitBuy(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        NewOrder sell = NewOrder.limitSell(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
        if (isSellFirst) {
            NewOrderResponse resp = client.newOrder(sell);
            Long sellOrderId = resp.getOrderId();
            if (sellOrderId != null) {
                trackBook.entry(sellOrderId, Side.SELL, price, quantity);
                resp = client.newOrder(buy);
                if (resp.getOrderId() != null) {
                    trackBook.entry(resp.getOrderId(), Side.BUY, price, quantity);
                } else {
                    logger.warn(" %s submit buy newOrder failed(after sell %s ): %s", instrument.asString(),
                            sellOrderId, buy.toString());
                }
            } else {
                logger.warn(" %s submit sell newOrder failed: %s", instrument.asString(), sell.toString());
            }
        } else {
            NewOrderResponse resp = client.newOrder(buy);
            Long buyOrderId = resp.getOrderId();
            if (buyOrderId != null) {
                trackBook.entry(buyOrderId, Side.BUY, price, quantity);
                resp = client.newOrder(buy);
                if (resp.getOrderId() != null) {
                    trackBook.entry(resp.getOrderId(), Side.SELL, price, quantity);
                } else {
                    logger.warn(" %s submit sell newOrder failed(after buy %s ): %s", instrument.asString(), buyOrderId,
                            buy.toString());
                }
            } else {
                logger.warn(" %s submit buy newOrder failed: %s", instrument.asString(), buy.toString());
            }
        }

    }

    private long getQuantity() {
        return 0l;
    }

}
