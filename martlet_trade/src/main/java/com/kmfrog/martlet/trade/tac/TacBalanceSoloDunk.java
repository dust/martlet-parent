package com.kmfrog.martlet.trade.tac;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacCancelExec;
import com.kmfrog.martlet.trade.exec.TacPlaceOrderExec;
import com.kmfrog.martlet.util.FeedUtils;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.Account;
import io.broker.api.client.domain.account.AssetBalance;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class TacBalanceSoloDunk extends InstrumentSoloDunk {

    private BigDecimal originBaseVolume;
    private BigDecimal originQuoteVolue;
    private BigDecimal currentBaseVolume;
    private BigDecimal currentQuoteVolume;
    private final AtomicLong lastOrder;
    private BrokerApiRestClient client;
    private int sleepMillis;

    public TacBalanceSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider, Param args,
            BrokerApiRestClient client) {
        super(instrument, src, trackBook, provider, args);
        this.client = client;
        this.originBaseVolume = args.getOriginBaseVolume();
        this.originQuoteVolue = args.getOriginQuoteVolume();
        this.sleepMillis = (minSleepMillis + maxSleepMillis) / 2;
        this.lastOrder = new AtomicLong(0L);
    }

    @Override
    public void placeHedgeOrder(long price, long spreadSize, IOrderBook book) {
        placeBBO();
        putBBO();
        retrieveBalance();
    }

    private void retrieveBalance() {
        Account acct = client.getAccount(3000L, System.currentTimeMillis());
        AssetBalance asset = acct.getAssetBalance(getBaseSymbol());
        currentBaseVolume = new BigDecimal(asset.getFree());
        // currentQuote

    }

    private String getBaseSymbol() {
        String sym = instrument.asString();
        if (sym.endsWith("USDT") || sym.endsWith("PTCN")) {
            return sym.substring(0, sym.length() - 4);
        }
        return sym.substring(0, sym.length() - 3);
    }

    private void putBBO() {
        RollingTimeSpan<TradeLog> logs = provider.getRollingTradeLog(source, instrument);
        if (Math.abs(logs.last() - logs.avg()) / (logs.last() * 1.0) > 0.1) {

            if (div(currentBaseVolume, originBaseVolume) <= 0.8) {
                putAskOrder();
            } else if (div(currentBaseVolume, originBaseVolume) >= 1.1) {
                putBidOrder();
            } else {
                if (System.currentTimeMillis() % 10 < 3) {
                    putBidOrder();
                } else {
                    putAskOrder();
                }
            }
        } else {
            logger.info("{} {} last trade price / avg, 0.9 or 1.1.", source, instrument.asString());
        }
        // 撤掉3档以外所有订单。
        cancelAfterLevel3Ask(lastBook);
        cancelAfterLevel3Bid(lastBook);
    }

    private double div(BigDecimal d1, BigDecimal d2) {
        return d1.divide(d2, MathContext.DECIMAL32).doubleValue();
    }

    private void putAskOrder() {
        if (System.currentTimeMillis() - lastOrder.get() > sleepMillis && !hasOccupy(Side.BUY, lastBook)) {
            // long abBid1 = abBook.getBestBidPrice();
            // long cbAsk1 = cbBook.getBestAskPrice();
            long bid1 = lastBook.getBestBidPrice();

            // if (abBid1 > 0 && cbAsk1 > 0) {
            // long caBidLimit = cbAsk1 / abBid1 * ca.getPriceFactor();

            // if ((caBidLimit - caBid1) / (caBid1 * 1.0) > 0.001) {
            // 距离3角套利还有安全距离
            long price = (bid1 + instrument.getPriceFactor()) / instrument.getShowPriceFactor();
            long volume = FeedUtils.between(vMin, vMax);
            TacPlaceOrderExec place = new TacPlaceOrderExec(instrument, price, volume, Side.BUY, client, trackBook);
            provider.submitExec(place);
            lastOrder.set(System.currentTimeMillis());
        }
    }

    private void putBidOrder() {
        if (System.currentTimeMillis() - lastOrder.get() > sleepMillis && !hasOccupy(Side.SELL, lastBook)) {
            // long abBid1 = abBook.getBestBidPrice();
            // long cbAsk1 = cbBook.getBestAskPrice();
            long ask1 = lastBook.getBestAskPrice();

            // if (abBid1 > 0 && cbAsk1 > 0) {
            // long caBidLimit = cbAsk1 / abBid1 * ca.getPriceFactor();

            // if ((caBidLimit - caBid1) / (caBid1 * 1.0) > 0.001) {
            long price = (ask1 - instrument.getPriceFactor()) / instrument.getShowPriceFactor();
            long volume = FeedUtils.between(vMin, vMax);
            TacPlaceOrderExec place = new TacPlaceOrderExec(instrument, price, volume, Side.SELL, client, trackBook);
            provider.submitExec(place);
            lastOrder.set(System.currentTimeMillis());
        }
    }

    private void cancelAfterLevel3Ask(IOrderBook caBook) {
        LongSortedSet prices = caBook.getAskPrices();
        long level3 = prices.size() > 3 ? prices.toArray(new long[prices.size()])[2] : 0;
        if (level3 > 0) {
            Set<Long> afterLevel3 = trackBook.getOrdersBetter(Side.SELL, level3);
            if (afterLevel3.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, trackBook);
                provider.submitExec(cancelExec);
            }
        }
    }

    private void cancelAfterLevel3Bid(IOrderBook caBook) {
        LongSortedSet prices = caBook.getBidPrices();
        long level3 = prices.size() > 3 ? prices.toArray(new long[prices.size()])[2] : 0;

        if (level3 > 0) {
            Set<Long> afterLevel3 = trackBook.getOrdersBetter(Side.BUY, level3);
            if (afterLevel3.size() > 0) {
                TacCancelExec cancelExec = new TacCancelExec(afterLevel3, client, provider, trackBook);
                provider.submitExec(cancelExec);
            }
        }
    }

    boolean hasOccupy(Side side, IOrderBook book) {
        PriceLevel openLevel = trackBook.getBestLevel(side);
        if (openLevel == null || book.getBestAskPrice() == 0 || book.getBestAskPrice() == 0) {
            return true; // 数据获取有问题。避免重新挂单，此处应该返回true。
        }

        long bboPrice = side == Side.SELL ? book.getBestAskPrice() : book.getBestBidPrice();
        if (openLevel.getPrice() != bboPrice) {
            return false;
        }

        long bboSize = side == Side.SELL ? book.getAskSize(bboPrice) : book.getBidSize(bboPrice);
        return openLevel.getSize() / (bboSize * 1.0) >= 0.9;
    }

    private void placeBBO() {
        PriceLevel openAskLevel = trackBook.getBestLevel(Side.SELL);
        if (openAskLevel != null) {
            long bestAskPrice = lastBook.getBestAskPrice();
            long bestAskSize = bestAskPrice > 0 ? lastBook.getAskSize(bestAskPrice) : 0L;
            if (bestAskSize > 0 && bestAskPrice == openAskLevel.getPrice()
                    && openAskLevel.getSize() / (bestAskSize * 1.0) >= 0.9) {
                if (bestAskSize < vMin) {
                    // 小于最小订单数量，先撤单（因为api会拒绝）
                    Set<Long> cancelIds = openAskLevel.getOrderIds();
                    TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
                    provider.submitExec(cancelExec);
                } else {
                    TacPlaceOrderExec placeBid = new TacPlaceOrderExec(instrument, bestAskPrice, bestAskSize, Side.BUY,
                            client, trackBook);
                    provider.submitExec(placeBid);
                }
            }
        }

        PriceLevel openBidLevel = trackBook.getBestLevel(Side.SELL);
        if (openBidLevel != null) {
            long bestBidPrice = lastBook.getBestBidPrice();
            long bestBidSize = bestBidPrice > 0 ? lastBook.getBidSize(bestBidPrice) : 0L;
            if (bestBidSize > 0 && bestBidPrice == openBidLevel.getPrice()
                    && openBidLevel.getSize() / (bestBidSize * 1.0) >= 0.9) {
                if (bestBidSize < vMin) {
                    Set<Long> cancelIds = openBidLevel.getOrderIds();
                    TacCancelExec cancelExec = new TacCancelExec(cancelIds, client, provider, trackBook);
                    provider.submitExec(cancelExec);
                } else {
                    TacPlaceOrderExec placeAsk = new TacPlaceOrderExec(instrument, bestBidPrice, bestBidSize, Side.SELL,
                            client, trackBook);
                    provider.submitExec(placeAsk);
                }
            }
        }

    }
}
