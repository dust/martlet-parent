package com.kmfrog.martlet.trade.utils;

import com.kmfrog.martlet.util.OrderIDGenerator;

public class OrderUtil {

	public static String generateHedgeClientOrderId() {
		return String.format("HEDGE_%s", new OrderIDGenerator().next());
	}
	
	public static boolean isHedgeOrder(String clientOrderId) {
		return clientOrderId != null && clientOrderId.indexOf("HEDGE") == 0;
	}
}
