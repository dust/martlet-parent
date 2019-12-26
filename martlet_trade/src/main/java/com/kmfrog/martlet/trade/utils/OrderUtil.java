package com.kmfrog.martlet.trade.utils;

import java.util.UUID;


public class OrderUtil {
	
	public static String generateHedgeClientOrderId() {
		return String.format("HEDGE_%s", UUID.randomUUID());
	}
	
	public static boolean isHedgeOrder(String clientOrderId) {
		return clientOrderId != null && clientOrderId.indexOf("HEDGE") == 0;
	}
}
