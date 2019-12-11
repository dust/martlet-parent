package com.kmfrog.martlet.maker.service;

import java.util.List;

import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.maker.model.entity.Order;


public interface DepthService {
	
    Integer cancelOpenOrder(Long orderId);
    
    Long insertOrder(Order order);
    
    List<Order> getOpenOrders(String symbol, Side side);
}

