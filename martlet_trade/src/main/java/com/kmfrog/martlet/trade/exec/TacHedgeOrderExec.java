package com.kmfrog.martlet.trade.exec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.util.FeedUtils;
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
public class TacHedgeOrderExec extends Exec {

    static Logger logger = LoggerFactory.getLogger(TacHedgeOrderExec.class);

    BrokerApiRestClient client;
    Provider provider;
    long price;
    long spread;
    TrackBook trackBook;
    boolean isSellFirst;
    Source src;
    Instrument instrument;
    final long vMin;
    final long vMax;
    final int avgSleepMillis;

    public TacHedgeOrderExec(Source src, Instrument instrument, long price, long spread, long vMin, long vMax,
            int avgSleep, BrokerApiRestClient client, TrackBook trackBook, Provider provider) {
        super(System.currentTimeMillis());
        this.src = src;
        this.instrument = instrument;
        this.price = price;
        this.spread = spread;
        this.vMin = vMin;
        this.vMax = vMax;
        this.avgSleepMillis = avgSleep;
        this.client = client;
        this.provider = provider;
        this.trackBook = trackBook;
        this.isSellFirst = spread < 5 || System.currentTimeMillis() % 100 < 80;

    }

    @Override
    public void run() {
        try {
            long quantity = getQuantity();
            if (quantity <= 0) {
                logger.info(this.instrument.asString() + " quantity=" + quantity);
                return;
            }
            String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits());
            String quantityStr = Fmt.fmtNum(quantity, instrument.getSizeFractionDigits());
            NewOrder buy = NewOrder.limitBuy(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
            NewOrder sell = NewOrder.limitSell(instrument.asString(), TimeInForce.GTC, quantityStr, priceStr);
            if (logger.isInfoEnabled()) {
                logger.info("buy:{}, sell: {}", buy.toString(), sell.toString());
            }
            if (isSellFirst) {
                NewOrderResponse resp = client.newOrder(sell);
                if (logger.isInfoEnabled()) {
                    logger.info("resp:{}", resp.toString());
                }
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
                if (logger.isInfoEnabled()) {
                    logger.info("resp:{}", resp.toString());
                }
                Long buyOrderId = resp.getOrderId();
                if (buyOrderId != null) {
                    trackBook.entry(buyOrderId, Side.BUY, price, quantity);
                    resp = client.newOrder(buy);
                    if (resp.getOrderId() != null) {
                        trackBook.entry(resp.getOrderId(), Side.SELL, price, quantity);
                    } else {
                        logger.warn(" %s submit sell newOrder failed(after buy %s ): %s", instrument.asString(),
                                buyOrderId, buy.toString());
                    }
                } else {
                    logger.warn(" %s submit buy newOrder failed: %s", instrument.asString(), buy.toString());
                }
            }
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }

    }

    private long getQuantity() {
        RollingTimeSpan<TradeLog> logs = provider.getRollingTradeLog(src, instrument);
        long lastMinuteVol = logs.sum() / (C.LAST_TRADE_WINDOW_MILLIS / 60000 * 2); // 因为trades记录了双边成交量。
        long expectMinuteVol = (vMin + vMax) / 2  * 60000 / avgSleepMillis;
        logger.info(instrument.asString()+"|lastMinuteVol:"+lastMinuteVol+"|"+expectMinuteVol);
        // 引入随机数，避免规律太明显。
        if (lastMinuteVol < expectMinuteVol || (lastMinuteVol < (long) (expectMinuteVol * 1.2) && System.currentTimeMillis() % 10 < 5)) {
            return FeedUtils.between(vMin, vMax);
        }
        return 0L;
    }

}
