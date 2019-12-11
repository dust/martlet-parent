package com.kmfrog.martlet.maker.service;

import com.kmfrog.martlet.maker.api.TatmasRestClient;
import com.kmfrog.martlet.maker.model.entity.Order;

public class TradeServiceImpl implements TradeService{
	
	private final TatmasRestClient client;
	
	public TradeServiceImpl(TatmasRestClient client) {
		this.client = client;
	}

	@Override
	public Long insertOrder(Order order) {
		return client.limitOrder(order.getSide(), order.getSymbol(), String.valueOf(order.getPrice()), String.valueOf(order.getVolume()));
	}

}
