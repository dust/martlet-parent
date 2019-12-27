package com.kmfrog.martlet.trade.tac;

import java.util.Set;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderEntry;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.InstrumentSoloDunk;
import com.kmfrog.martlet.trade.Provider;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacHedgeOrderExec;
import com.kmfrog.martlet.trade.utils.OrderUtil;

import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class TacInstrumentSoloDunk extends InstrumentSoloDunk {

    BrokerApiRestClient client;
    private final long hedgeExistTime = 5000;

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
        if(hasUnfilledHedge() || isDepthUnNormal(book)) {
        	return;
        }
        int avgSleepMillis = (minSleepMillis + maxSleepMillis) / 2;
        provider.submitExec(new TacHedgeOrderExec(source, instrument, price, spreadSize, vMin, vMax, avgSleepMillis,
                client, trackBook, provider));
    }
    
    /**
     * 买一或者卖一不是对敲
     * @param lastBook
     * @return
     */
    private boolean isDepthUnNormal(IOrderBook lastBook) {
    	long ask1 = lastBook.getBestAskPrice();
    	long bid1 = lastBook.getBestBidPrice();
    	
    	long ask1Size = lastBook.getAskSize(ask1);
    	long bid1Size = lastBook.getBidSize(bid1);
    	
    	PriceLevel askLevel = trackBook.getPriceLevel(Side.SELL, ask1);
    	PriceLevel bidLevel = trackBook.getPriceLevel(Side.BUY, bid1);

    	long unitPrice = 1 * C.POWERS_OF_TEN[instrument.getPriceFractionDigits() - instrument.getShowPriceFractionDigits()];
    	//买一不是自己的订单,并且数量少于最小下单量
    	if(askLevel == null && ask1Size <= vMin) {
    		if(!isLevelSpaceNormal(Side.SELL) || (ask1 - bid1) < unitPrice * 4) {
    			return true;
    		}
    	}
    	//卖一不是自己的订单,并且数量少于最小下单量
    	if(bidLevel == null && bid1Size <= vMin) {
    		if(!isLevelSpaceNormal(Side.BUY) || (ask1 - bid1) < unitPrice * 4) {
    			return true;
    		}
    	}
    	
    	return false;
    }
    
    /**
     * 盘口买一买二 或者 卖一卖二 间隔是否正常
     * @param side
     * @return
     */
    private boolean isLevelSpaceNormal(Side side) {
    	LongSortedSet prices = side == Side.SELL ? lastBook.getAskPrices() : lastBook.getBidPrices();
    	if(prices.size() < 3) {
    		return true;
    	}
    	Long[] pricesArray = prices.toArray(new Long[prices.size()]);
    	long level1 = side == Side.SELL ? pricesArray[0] : pricesArray[3];
    	long level2 = pricesArray[1];
    	long level3 = side == Side.SELL ? pricesArray[2] : pricesArray[0];
//    	System.out.println(String.format("###### %s %s %s %s %s", String.valueOf(level1), String.valueOf(level2), String.valueOf(level3), String.valueOf((level2 * 1.0) / (level1 * 1.0)), String.valueOf((level3 * 1.0) / (level2 * 1.0))));
    	if((level2 * 1.0) / (level1 * 1.0) > 1.10 /* || (level3 * 1.0) / (level2 * 1.0) > 1.2 */) {
    		return false;
    	}
    	return true;
    }

    /**
     * 存在限定时间内未对敲成功的订单
     * @return
     */
    private boolean hasUnfilledHedge() {
    	long concurrentTime = System.currentTimeMillis();
    	Set<Long> askIds = trackBook.getOrders(Side.SELL);
    	Set<Long> bidIds = trackBook.getOrders(Side.BUY);
    	if(askIds.size() > 0) {
    		Long[] askOrderIds = new Long[askIds.size()];
    		askIds.toArray(askOrderIds);
    		for(int i=0;i<askOrderIds.length;i++) {
    			OrderEntry order = trackBook.getOrder(askOrderIds[i]);
        		long createTime = order.getCreateTime();
        		if(OrderUtil.isHedgeOrder(order.getClientOrderId()) && createTime > 0 && concurrentTime - createTime > hedgeExistTime) {
        			return true;
        		}
    		}
    	}
    	
    	if(bidIds.size() > 0) {
    		Long[] bidOrderIds = new Long[bidIds.size()];
    		for(int i=0;i<bidOrderIds.length; i++) {
    			bidIds.toArray(bidOrderIds);
    			OrderEntry order = trackBook.getOrder(bidOrderIds[i]);
    			long createTime = order.getCreateTime();
    			if(OrderUtil.isHedgeOrder(order.getClientOrderId()) && createTime > 0 && concurrentTime - createTime > hedgeExistTime) {
    				return true;
    			}
    		}
    	}
    	
    	return false;
    }
}
