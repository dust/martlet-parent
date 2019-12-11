package com.kmfrog.martlet.maker.api;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Side;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TatmasRestClient {
	
	static Logger logger = LoggerFactory.getLogger(TatmasRestClient.class);
	
	private final String apiKey;
	private final String secret;
	private final String signatureFmt = "AccessKeyId=%s&SignatureMethod=HmacSHA256&SignatureVersion=1&Timestamp=%s";
	
	public TatmasRestClient(String apiKey, String secret) {
		this.apiKey = apiKey;
		this.secret = secret;
	}
	
	/**
	 * 查询委托成交明细
	 * @param orderId
	 * @return
	 */
	public String getOrderDetail(String orderId) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("orderId", orderId);
		
		try {
			Response res = doPost("http://tatmas-exchange.com/exchange/order/open/detail", params);
			if(res.isSuccessful()) {
				return res.body().string();
			}
		}catch(Exception ex) {
			logger.warn("getOrderDetail exception {}: {}", ex.getMessage(), orderId);
		}
		return null;
	}
	
	/**
	 * 
	 * @param symbol BTC/USDT
	 * @param startTime 毫秒时间戳
	 * @param endTime 毫秒时间戳
	 * @param side
	 * @param status 0，交易中，1，已完成，2，已取消，3，结束超时
	 * @param pageNo
	 * @param pageSize
	 * @return 
	 * 
	 *  {"content":[{"orderId":"E157598654446490","memberId":6148,"type":"LIMIT_PRICE","amount":0.00100000,"symbol":"BTC/USDT","tradedAmount":0E-16,"turnover":0E-16,"coinSymbol":"BTC","baseSymbol":"USDT","status":"CANCELED","direction":"BUY","price":5000.00000000,"time":1575986544464,"completedTime":null,"canceledTime":1575987356767,"useDiscount":"0","detail":[],"completed":true}],"last":true,"totalElements":1,"totalPages":1,"first":true,"sort":[{"direction":"DESC","property":"time","ignoreCase":false,"nullHandling":"NATIVE","ascending":false,"descending":true}],"numberOfElements":1,"size":10,"number":0}
	 */
	public String getHistoryOrder(String symbol, long startTime, long endTime, Side side, int status, int pageNo, int pageSize) {
		Map<String, String> params = new HashMap<String, String>();
		String direction = side == Side.BUY ? "0" : "1";
		params.put("type", "1");
		params.put("symbol", symbol);
		params.put("startTime", String.valueOf(startTime));
		params.put("endTime", String.valueOf(endTime));
		params.put("direction", direction);
		params.put("status", String.valueOf(status));
		params.put("pageNo", String.valueOf(pageNo));
		params.put("pageSize", String.valueOf(pageSize));
		
		try {
			Response res = doPost("http://tatmas-exchange.com/exchange/order/open/personal/history", params);
			if(res.isSuccessful()) {
				return res.body().string();
			}
		}catch(Exception ex) {
			logger.warn(" getHistoryOrder exception {}: {} {} {} {} {} {} {}", ex.getMessage(), symbol, String.valueOf(startTime), String.valueOf(endTime), direction, status, pageNo, pageSize);
		}
		
		return null;
	}
	
	public boolean cancelOrder(Long orderId) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("orderId", String.valueOf(orderId));
		
		try {
			Response res = doPost("http://tatmas-exchange.com/exchange/order/open/cancel", params);
			if(res.isSuccessful()) {
				JSONObject data = JSONObject.parseObject(res.body().string());
				if(data.containsKey("code") && data.getInteger("code") != 0) {
					logger.warn(" cancelOrder failed {}: {}", data.getString("message"), orderId);
					return false;
				}
				return true;
			}
		}catch(Exception ex) {
			logger.warn(" cancelOrder exception {}: {}", ex.getMessage(), orderId);
		}
		return false;
	}
	
	/**
	 * 
	 * @param symbol BTC/USDT
	 * @param startTime 毫秒时间戳
	 * @param endTime 毫秒时间戳
	 * @param side
	 * @param status 0，交易中，1，已完成，2，已取消，3，结束超时
	 * @param pageNo
	 * @param pageSize
	 * @return
	 */
	public String getOpenOrder(String symbol, Side side, int pageNo, int pageSize) {
		Map<String, String> params = new HashMap<String, String>();
		String direction = side == Side.BUY ? "0" : "1";
		params.put("type", "1");
		params.put("symbol", symbol);
//		params.put("startTime", String.valueOf(startTime));
		params.put("startTime", "");
//		params.put("endTime", String.valueOf(endTime));
		params.put("endTime", "");
		params.put("direction", direction);
		params.put("pageNo", String.valueOf(pageNo));
		params.put("pageSize", String.valueOf(pageSize));
		
		try {
			Response res = doPost("http://tatmas-exchange.com/exchange/order/open/personal/current", params);
			System.out.println(res.body().string());
//			if(res.isSuccessful()) {
//				return res.body().string();
//			}
		}catch(Exception ex) {
			logger.warn(" getOpenOrder exception {}: {} {} {} {}", ex.getMessage(), symbol, direction, pageNo, pageSize);
		}
		return null;
	}
	
	/**
	 * 
	 * @param symbol
	 * @param price
	 * @param amount
	 * @return 订单号 ""
	 */
	public Long limitBuy(String symbol, String price, String amount) {
		return limitOrder(Side.BUY, symbol, price, amount);
	}
	
	/**
	 * 
	 * @param symbol
	 * @param price
	 * @param amount
	 * @return 订单号 ""
	 */
	public Long limitSell(String symbol, String price, String amount) {
		return limitOrder(Side.SELL, symbol, price, amount);
	}
	
	/**
	 * 
	 * @param side
	 * @param symbol
	 * @param price
	 * @param amount
	 * @return
	 * 
	 * {"code":0,"message":"success","totalPage":null,"totalElement":null,"data":"E157602986649923"}
	 */
	public Long limitOrder(Side side, String symbol, String price, String amount) {
		Map<String, String> params = new HashMap<String, String>();
		String direction = side == Side.BUY ? "0" : "1";
		params.put("type", "1"); // 0,市价 1,限价
		params.put("direction", direction);
		params.put("symbol", symbol);
		params.put("price", price);
		params.put("amount", amount);
		
		try {
			Response res = doPost("http://tatmas-exchange.com/exchange/order/open/add", params);
			if(res.isSuccessful()) {
				String str = res.body().string();
				JSONObject data = JSONObject.parseObject(str);
				return data.getLong("data");
			}
		}catch(Exception ex) {
			logger.warn(" limitOrder exception {}: {} {} {} {}", direction, symbol, price, amount);
		}
		return null;
	}
	
	/**
	 * 
	 * @param symbol
	 * @return
	 * {"ask":{"minAmount":9.99500000,"highestPrice":7403.37380000,"symbol":"BTC/USDT","lowestPrice":7403.37380000,"maxAmount":9.99500000,"items":[{"amount":9.99500000,"price":7403.37380000}],"direction":"SELL"},"bid":{"minAmount":0.00100000,"highestPrice":7143.70910000,"symbol":"BTC/USDT","lowestPrice":5000.00000000,"maxAmount":0.50000000,"items":[{"amount":0.50000000,"price":7143.70910000},{"amount":0.00100000,"price":5000.00000000}],"direction":"BUY"}}
	 */
	public String getDepth(String symbol) {
		Map<String, String> params = new HashMap<String, String>();
		params.put("symbol", symbol);
		try {
			Response res = doPost("http://tatmas-exchange.com/market/open/exchange-plate-full", params);
			if(res.isSuccessful()) {
				return res.body().string();
			}
		}catch (Exception ex) {
			logger.warn(" getDepth exception {}: {}", ex.getMessage(), symbol);
		}
		return null;
	}
	
	private Response doPost(String url, Map<String, String> params) throws Exception{
		String data = String.format(this.signatureFmt, apiKey, System.currentTimeMillis());
		String signature=URLEncoder.encode(TatmasRestClient.HMACSHA256(data, secret), "UTF-8");
		StringBuilder reqBody = new StringBuilder();
		reqBody.append(data).append("&Signature=").append(signature);
		
		String[] keys = new String[params.size()];
		params.keySet().toArray(keys);
		for(int i=0;i<keys.length;i++) {
			String key = keys[i];
			reqBody.append("&").append(key).append("=").append(params.get(key));
		}
		
		OkHttpClient client = new OkHttpClient();
		MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
		RequestBody body = RequestBody.create(mediaType, reqBody.toString());
		Request request = new Request.Builder()
				  .url(url)
				  .post(body)
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();
		return client.newCall(request).execute();
	}

  	/**
     * 生成 HMACSHA256
     * @param data 待处理数据
     * @param key 密钥
     * @return 加密结果
     * @throws Exception
     */
    public static String HMACSHA256(String data, String key) {
    	try {
	        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
	        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
	        sha256_HMAC.init(secret_key);
	        byte[] array = sha256_HMAC.doFinal(data.getBytes());
	        return Base64.encodeBase64String(array);
    	}catch(Exception ex) {
    		
    	}
    	return "";
    }

    public static void main(String[] args) {
    	try {
	    	SimpleDateFormat format =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	    	long startTime = format.parse("2019-12-01 00:00:00").getTime();
	    	long endTime = format.parse("2019-12-30 00:00:00").getTime();
	    	
	    	String apiKey = "34b0792b-e292-45fe-a9a3-6b3eb53d4912";
	    	String secret = "e7fe3eff-8b64-4574-b476-af55314d95aa";
	    	TatmasRestClient client = new TatmasRestClient(apiKey, secret);
//	    	System.out.println(client.getDepth("BTC/USDT"));
//	    	System.out.println(client.limitBuy("BTC/USDT", "5000", "0.001"));
//	    	System.out.println(client.cancelOrder("E157602986649923"));
//	    	System.out.println(client.getOrderDetail("E157598654446490"));
	    	
//	    	System.out.println(client.getHistoryOrder("BTC/USDT", startTime, endTime, Side.BUY, 2, 1, 10));
//	    	System.out.println(client.getOpenOrder("BTC/USDT", Side.BUY, 1, 10));
    	} catch(Exception ex) {
    		ex.printStackTrace();
    	}
    }
}
