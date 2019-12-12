package com.kmfrog.martlet.maker.service;

import com.kmfrog.martlet.maker.model.entity.Order;

public interface TradeService {

	Long insertOrder(Order order, String api, String secret);
}
