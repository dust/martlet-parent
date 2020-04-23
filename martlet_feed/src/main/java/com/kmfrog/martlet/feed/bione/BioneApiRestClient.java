package com.kmfrog.martlet.feed.bione;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.checkerframework.common.reflection.qual.NewInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.google.inject.spi.Message;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.BaseApiRestClient;

import it.unimi.dsi.fastutil.Hash;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BioneApiRestClient extends BaseApiRestClient{
	
	static Logger logger = LoggerFactory.getLogger(BioneApiRestClient.class);
	
	public BioneApiRestClient(String baseUrl, String apiKey, String secret) {
		super(baseUrl, apiKey, secret);
	}

	@Override
	public JSONObject getDepth(String symbol) {
		// TODO Auto-generated method stub
		return null;
	}
	
	public JSONObject getAccount(String symbol) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("code", formatSymbol(symbol));
		params.put("accessKey", this.apiKey);
		
		return doGet("/api/v2/account/bill/:currencyCode?", params);
	}
	
	public boolean cancelOrder(String symbol, String orderId) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("accessKey", this.apiKey);
		params.put("market", formatSymbol(symbol));
		params.put("orderid", orderId);
		
		doGet("/api/v2/order/cancel", params);
		return true;
	}
	
	public JSONObject getOrders(String symbol, int status) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("accessKey", this.apiKey);
		params.put("market", formatSymbol(symbol));
		params.put("status", status); // 1未成交 2已成交 3已取消
		
		return doGet("/api/v2/orders", params);
	}
	
	public boolean batchCancel(String symbol, String[] orderIds) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("accessKey", this.apiKey);
		params.put("market", formatSymbol(symbol));
		StringBuilder sb = new StringBuilder();
		for(String orderId: orderIds) {
			sb.append(orderId).append(",");
		}
		params.put("orderids", sb.toString().substring(0, sb.length()-1));
		Response res = null;
		try {
			res = doDelete("/api/v2/order/batchcancel", params);
			if(res.isSuccessful()) {
				String str = res.body().string();
				System.out.println(str);
			}else {
				System.out.println(res.body().string());
			}
		} catch(Exception ex) {
			ex.printStackTrace();
		} finally {
			if(res != null) {
				res.body().close();
			}
		}
		return true;
	}
	
	public Long limitBuy(String symbol, String amount, String price) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("accessKey", this.apiKey);
		params.put("market", formatSymbol(symbol));
		params.put("type", 1);
		params.put("number", amount);
		params.put("unitPrice", price);
		
		Response res = null;
		try {
			res = doPost("/api/v2/order/create", params);
			if(res.isSuccessful()) {
				String str = res.body().string();
				System.out.println(str);
				DefaultJSONParser parser = new DefaultJSONParser(str);
				JSONObject root = parser.parseObject();
				return root.getJSONObject("data").getLong("orderid");
			} 
		} catch(Exception ex) {
			ex.printStackTrace();
		} finally {
			if(res != null) {
        		res.body().close();
        	}
		}
		
		return null;
	}
	
	public Long limitSell(String symbol, String amount, String price) {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("accessKey", this.apiKey);
		params.put("market", formatSymbol(symbol));
		params.put("type", 2);
		params.put("number", amount);
		params.put("unitPrice", price);
		
		Response res = null;
		try {
			res = doPost("/api/v2/order/create", params);
			if(res.isSuccessful()) {
				String str = res.body().string();
				System.out.println(str);
				DefaultJSONParser parser = new DefaultJSONParser(str);
				JSONObject root = parser.parseObject();
				return root.getJSONObject("data").getLong("orderid");
			} 
		} catch(Exception ex) {
			ex.printStackTrace();
		} finally {
			if(res != null) {
        		res.body().close();
        	}
		}
		
		return null;
	}
	
	public Response doPost(String url, Map<String, Object> params) throws Exception{
		String[] keys = new String[params.keySet().size()];
		params.keySet().toArray(keys);
		Arrays.sort(keys);
		
		StringBuilder p = new StringBuilder();
		StringBuilder reqBody = new StringBuilder();
		p.append(this.secret);
		for(String key: keys) {
			p.append(key).append(params.get(key));
			reqBody.append(key).append("=").append(params.get(key)).append("&");
		}
		p.append(this.secret);
		byte[] hmac = HMACSHA256(p.toString(), secret);
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] b1 = secret.getBytes("utf-8");
			byte[] b2 = new byte[b1.length+hmac.length+b1.length];
			System.arraycopy(b1, 0, b2, 0, b1.length);
			System.arraycopy(hmac, 0, b2, b1.length, hmac.length);
			System.arraycopy(b1, 0, b2, b1.length+hmac.length, b1.length);
			byte[] bytes = md.digest(b2);
			
			String sign = bytesToHex(bytes);
			reqBody.append("sign=").append(sign);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, reqBody.toString());
        Request request = new Request.Builder().url(baseUrl+url).post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").build();
        return client.newCall(request).execute();        
	}
	
	public Response doDelete(String url, Map<String, Object> params) throws Exception{
		String[] keys = new String[params.keySet().size()];
		params.keySet().toArray(keys);
		Arrays.sort(keys);
		
		StringBuilder p = new StringBuilder();
		StringBuilder reqBody = new StringBuilder();
		p.append(this.secret);
		for(String key: keys) {
			p.append(key).append(params.get(key));
			reqBody.append(key).append("=").append(params.get(key)).append("&");
		}
		p.append(this.secret);
		byte[] hmac = HMACSHA256(p.toString(), secret);
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] b1 = secret.getBytes("utf-8");
			byte[] b2 = new byte[b1.length+hmac.length+b1.length];
			System.arraycopy(b1, 0, b2, 0, b1.length);
			System.arraycopy(hmac, 0, b2, b1.length, hmac.length);
			System.arraycopy(b1, 0, b2, b1.length+hmac.length, b1.length);
			byte[] bytes = md.digest(b2);
			
			String sign = bytesToHex(bytes);
			reqBody.append("sign=").append(sign);
		} catch (Exception e) {
			// TODO: handle exception/api/v2/order/batchcancel
		}
		
		OkHttpClient client = new OkHttpClient();

		MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, reqBody.toString());
        Request request = new Request.Builder().url(baseUrl+url).delete(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").build();
        return client.newCall(request).execute();        
	}
	
	public JSONObject doGet(String url, Map<String, Object> params) {
		String[] keys = new String[params.keySet().size()];
		params.keySet().toArray(keys);
		Arrays.sort(keys);
		
		StringBuilder p = new StringBuilder();
		StringBuilder reqBody = new StringBuilder();
		p.append(this.secret);
		for(String key: keys) {
			p.append(key).append(params.get(key));
			reqBody.append(key).append("=").append(params.get(key)).append("&");
		}
		p.append(this.secret);
		byte[] hmac = HMACSHA256(p.toString(), secret);
		
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] b1 = secret.getBytes("utf-8");
			byte[] b2 = new byte[b1.length+hmac.length+b1.length];
			System.arraycopy(b1, 0, b2, 0, b1.length);
			System.arraycopy(hmac, 0, b2, b1.length, hmac.length);
			System.arraycopy(b1, 0, b2, b1.length+hmac.length, b1.length);
			byte[] bytes = md.digest(b2);
			
			String sign = bytesToHex(bytes);
			reqBody.append("sign=").append(sign);
		} catch (Exception e) {
			// TODO: handle exception
		}
		
		OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
				  .url(baseUrl + url + "?"+reqBody.toString())
				  .get()
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();

		try {
			Response res =  client.newCall(request).execute();
			if(res.isSuccessful()) {
				String msg = res.body().string();
				System.out.println(msg);
				DefaultJSONParser parser = new DefaultJSONParser(msg);
				return parser.parseObject();
			}else {
				System.out.println(res.body().string());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			
		}
		return null;
		
	}
	
	private static String bytesToHex(byte[] md5Array) {
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < md5Array.length; i++) {
            int temp = 0xff & md5Array[i];
            String hexString = Integer.toHexString(temp);
            if (hexString.length() == 1) {
                strBuilder.append("0").append(hexString);
            } else {
                strBuilder.append(hexString);
            }
        }
        return strBuilder.toString();
    }
    
	/**
     * 生成 HMACSHA256
     * 
     * @param data 待处理数据
     * @param key 密钥
     * @return 加密结果
     * @throws Exception
     */
    public static byte[] HMACSHA256(String data, String key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] array = sha256_HMAC.doFinal(data.getBytes());
            return array;
            
//            return Base64.encodeBase64String(array);
        } catch (Exception ex) {

        }
        return null;
    }

    private String formatSymbol(String symbol) {
    	String[] keyCoins = {"BTC", "USDT", "ETH"};
    	String retString = symbol;
    	for(int i=0; i<keyCoins.length; i++) {
    		retString = formatSymbolByKeyCoin(symbol, keyCoins[i]);
    		if(retString.contains("_")) {
    			break;
    		}
    	}
    	return retString.toLowerCase();
    }
    
    private String formatSymbolByKeyCoin(String symbol, String keyCoin) {
    	if(symbol.startsWith(keyCoin)) {
    		return symbol.replace(keyCoin, keyCoin+"_");
    	}else if(symbol.endsWith(keyCoin)) {
    		return symbol.replace(keyCoin, "_"+keyCoin);
    	}
    	return symbol;
    }
    
    public static void main(String[] args) {
    }
}
