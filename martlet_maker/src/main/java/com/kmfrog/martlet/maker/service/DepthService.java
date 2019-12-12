package com.kmfrog.martlet.maker.service;

import java.util.List;

import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.maker.model.entity.Order;


public interface DepthService {
	
    Integer cancelOpenOrder(Long orderId, String api, String secret);
    
    Long insertOrder(Order order, String api, String secret);
    
    List<Order> getOpenOrders(String symbol, Side side, Integer userId, String api, String secret);
}

