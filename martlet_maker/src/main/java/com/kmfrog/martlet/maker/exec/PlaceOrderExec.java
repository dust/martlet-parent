package com.kmfrog.martlet.maker.exec;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.maker.model.entity.Order;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;

public class PlaceOrderExec extends Exec{
	
	protected TrackBook trackBook;
	protected List<Order> orders;
	DepthService depthService;
	TradeService tradeService;
	Instrument instrument;
	Logger logger;

	public PlaceOrderExec(Instrument instrument, List<Order> orders, DepthService depthService, TradeService tradeService, TrackBook trackBook, Logger logger) {
		super(System.currentTimeMillis());
		this.trackBook = trackBook;
		this.orders = orders;
		this.depthService = depthService;
		this.tradeService = tradeService;
		this.instrument = instrument;
		this.logger = logger;
	}

	@Override
	public void run() {
		try {
		    int initStatus = 0;
			orders.stream().forEach((o)->{
				depthService.insertOrder(o);
				long price = o.getPrice().multiply(BigDecimal.valueOf(instrument.getPriceFactor())).longValue();
                long size = o.getVolume().multiply(BigDecimal.valueOf(instrument.getSizeFactor())).longValue();
                trackBook.entry(o.getId(), o.getSide(), price, size, initStatus);
				
			});
		} catch (Exception ex) {
			logger.warn("{} placeOrder {}, {}", instrument.asString(), ex.getMessage(), orders.toString());
		}
	}

}
