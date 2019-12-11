package com.kmfrog.martlet.maker.service;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.maker.api.TatmasRestClient;
import com.kmfrog.martlet.maker.model.entity.Order;

public class DepthServiceImpl implements DepthService{
	
	private final TatmasRestClient client;
	
	public DepthServiceImpl(TatmasRestClient client) {
		this.client = client;
	}
	

	@Override
	public Integer cancelOpenOrder(Long orderId) {
		boolean isSuccess = client.cancelOrder(orderId);
		if(isSuccess) {
			return 1;
		}else {
			return 0;
		}
	}

	@Override
	public Long insertOrder(Order order) {
		return client.limitOrder(order.getSide(), order.getSymbol(), String.valueOf(order.getPrice()), String.valueOf(order.getVolume()));
	}


	@Override
	public List<Order> getOpenOrders(String symbol, Side side, Integer userId) {
		int pageNo = 1;
		int pageSize = 1000;
		String text = client.getOpenOrder(symbol, side, pageNo, pageSize);
		
		List<Order> ret = new ArrayList<Order>();
		
		JSONObject data = JSONObject.parseObject(text);
		JSONArray content = data.getJSONArray("content");
		for(Object o: content) {
			JSONObject orderJson = (JSONObject)o;
			Side sideObj = orderJson.getString("direction").equals("BUY") ? Side.BUY : Side.SELL;
			BigDecimal price = orderJson.getBigDecimal("price");
            BigDecimal amount = orderJson.getBigDecimal("amount");
            Order order = Order.buildOrderByPriceLevel(orderJson.getString("symbol"), sideObj, price, amount, userId);
			ret.add(order);
		}
		
		return ret;
	}

}
