package com.kmfrog.martlet.trade.exec;

import java.util.concurrent.atomic.AtomicLong;

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
import com.kmfrog.martlet.trade.bikun.BikunApiRestClient;
import com.kmfrog.martlet.util.FeedUtils;
import com.kmfrog.martlet.util.Fmt;

public class BikunHedgeOrderExec extends Exec{
	
	static Logger logger = LoggerFactory.getLogger(BikunHedgeOrderExec.class);

	BikunApiRestClient client;
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

    public BikunHedgeOrderExec(Source src, Instrument instrument, long price, long spread, long vMin, long vMax,
            int avgSleep, BikunApiRestClient client, TrackBook trackBook, Provider provider) {
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
//        this.isSellFirst = spread < 5 || System.currentTimeMillis() % 100 < 80;
        this.isSellFirst = System.currentTimeMillis() %100 < 80;

    }

    @Override
    public void run() {
        try {
            long quantity = getQuantity();
            if (quantity <= 0) {
                logger.info(instrument.asString()+ price + "|"+ vMin+"|"+vMax + " quantity=" + quantity);
                return;
            }
            String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits(), instrument.getShowPriceFractionDigits());
            String quantityStr = Fmt.fmtNum(quantity, instrument.getSizeFractionDigits());
            if (logger.isInfoEnabled()) {
                logger.info("hedge:{} {} {}", instrument.asString(), quantityStr, priceStr);
            }
            if (isSellFirst) {
            	Long sellOrderId = client.limitSell(instrument, quantityStr, priceStr);
                if (sellOrderId != null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("resp: limitSell {} {} {}", quantityStr, priceStr, sellOrderId.toString());
                    }
                    trackBook.entry(sellOrderId, Side.SELL, price, quantity);
                    Long buyOrderId = client.limitBuy(instrument, quantityStr, priceStr);
                    if (buyOrderId != null) {
                        trackBook.entry(buyOrderId, Side.BUY, price, quantity);
                    } else {
                        logger.warn(" {} submit buy newOrder failed(after sell {} ): {} {}", instrument.asString(),
                                sellOrderId.toString(), quantityStr, priceStr);
                    }
                } else {
                    logger.warn(" {} submit sell newOrder failed: {}　{}", instrument.asString(), quantityStr, priceStr);
                }
            } else {
            	Long buyOrderId = client.limitBuy(instrument, quantityStr, priceStr);
                if (buyOrderId != null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("resp: limitBuy {} {} {}", quantityStr, priceStr, buyOrderId.toString());
                    }
                    trackBook.entry(buyOrderId, Side.BUY, price, quantity);                    
                    Long sellOrderId = client.limitSell(instrument, quantityStr, priceStr);
                    if (sellOrderId != null) {
                        trackBook.entry(sellOrderId, Side.SELL, price, quantity);
                    } else {
                        logger.warn(" {} submit sell newOrder failed(after buy {} ): {} {}", instrument.asString(),
                                buyOrderId.toString(), quantityStr, priceStr);
                    }
                } else {
                    logger.warn(" {} submit buy newOrder failed: {} {}", instrument.asString(), quantityStr, priceStr);
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
