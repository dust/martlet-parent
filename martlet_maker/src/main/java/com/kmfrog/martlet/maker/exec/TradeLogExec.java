package com.kmfrog.martlet.maker.exec;

import org.slf4j.Logger;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.maker.core.Provider;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;

public class TradeLogExec extends Exec{
	
	TradeService tradeService;
	DepthService depthService;
	TradeLog log;
	TradeLog lastLog;
	Source src;
	Instrument instrument;
	Provider provider;
	Logger logger;

	public TradeLogExec(TradeLog log, Source src, Instrument instrument, Provider provider, Logger logger) {
		super(System.currentTimeMillis());
		this.log = log;
		this.src = src;
		this.instrument = instrument;
		this.provider = provider;
		this.logger = logger;
		this.tradeService = provider.getTradeService();
		this.depthService = provider.getDepthService();
	}

	@Override
	public void run() {
		try {
			IOrderBook book = provider.getOrderBook(src, instrument);
			long bestOffer = book.getBestAskPrice();
            long bestBid = book.getBestBidPrice();
            long tradePrice = log.getPrice();
            
            if (tradePrice < bestBid || tradePrice > bestOffer) {
                if(logger.isInfoEnabled()) {
                    logger.info("{} {} trade log out of book: {},{},{}", src, instrument.asString(), bestBid, bestOffer, tradePrice);
                }
                return;
            }
            
            TrackBook trackBook = provider.getTrackBook(Source.Bitrue, instrument);
            long openBid1 = trackBook.getBestLevel(Side.BUY) != null ? trackBook.getBestLevel(Side.BUY).getPrice() : 0L;
            long openAsk1 = trackBook.getBestLevel(Side.SELL) != null ? trackBook.getBestLevel(Side.SELL).getPrice()
                    : 0L;
            if (tradePrice < openBid1 || tradePrice > openAsk1) {
                if(logger.isInfoEnabled()) {
                    logger.info("{} {} trade log out of open order: {},{},{}", src, instrument.asString(), openBid1, openAsk1, tradePrice);
                }
                return;
            }
            
            
            
		}catch(Exception ex) {
			logger.warn("{} {} trade exec {}", src, instrument.asString(), ex.getMessage());
		}
	}

}
