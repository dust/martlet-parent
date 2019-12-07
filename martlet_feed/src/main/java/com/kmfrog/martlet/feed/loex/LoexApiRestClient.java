package com.kmfrog.martlet.feed.loex;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.BaseApiRestClient;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoexApiRestClient extends BaseApiRestClient{
	static Logger logger = LoggerFactory.getLogger(LoexApiRestClient.class);
	
	public LoexApiRestClient(String baseUrl, String apiKey, String secret) {
		super(baseUrl, apiKey, secret);
	}
	
	public Long limitBuy(Instrument instrument, String quantityStr, String priceStr) {
		Response res = this.limitOrder("BUY", instrument.asString().toLowerCase(), quantityStr, priceStr);
		if(res.isSuccessful()) {
			try {
				String str = res.body().string();
				System.out.println(str);
				JSONObject jsonObject = JSONObject.parseObject(str);
				if(jsonObject.getIntValue("code") == 0) {
					return jsonObject.getJSONObject("data").getLong("order_id");
				}else {
					logger.warn(" {} submit buy newOrder failed: {} {} {}", instrument.asString(), quantityStr, priceStr);
					return null;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	public Long limitSell(Instrument instrument, String quantityStr, String priceStr) {
		Response res = this.limitOrder("SELL", instrument.asString().toLowerCase(), quantityStr, priceStr);
		if(res.isSuccessful()) {
			try {
				String str = res.body().string();
				System.out.println(str);
				JSONObject jsonObject = JSONObject.parseObject(str);
				if(jsonObject.getIntValue("code") == 0) {
					return jsonObject.getJSONObject("data").getLong("order_id");
				}else {
					logger.warn(" {} submit sell newOrder failed:　{} {}", instrument.asString(), quantityStr, priceStr);
					return null;
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}
	
	private Response limitOrder(String side, String symbol, String volume, String price) {
		HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("api_key", apiKey);
        params.put("symbol", symbol.toLowerCase());
        params.put("time", System.currentTimeMillis()/1000);
        params.put("symbol", symbol);
        params.put("volume", volume);
        params.put("side", side);
        params.put("type", "1");
        params.put("price", price);

        String[] keys = new String[params.keySet().size()];
        params.keySet().toArray(keys);
        Arrays.sort(keys);

        StringBuilder p = new StringBuilder();
        StringBuilder reqBody = new StringBuilder();
        for (String key : keys) {
            p.append(key).append(params.get(key));
            reqBody.append(key).append("=").append(params.get(key)).append("&");
        }
        p.append(secret);
        
        MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] bytes = md.digest(p.toString().getBytes("utf-8"));
	        String sign = bytesToHex(bytes);
	        reqBody.append("sign=").append(sign);
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        OkHttpClient client = new OkHttpClient();
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, reqBody.toString());
		Request request = new Request.Builder()
				  .url(baseUrl + "/open/api/create_order")
				  .post(body)
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();

		try {
			return client.newCall(request).execute();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			
		}
		return null;
	}
	
	@Override
	public JSONObject getDepth(String symbol) {
		HashMap<String, Object> params = new HashMap<String, Object>();
        params.put("api_key", apiKey);
        params.put("symbol", symbol.toLowerCase());
        params.put("type", "step0");
        params.put("time", System.currentTimeMillis()/1000);

        String[] keys = new String[params.keySet().size()];
        params.keySet().toArray(keys);
        Arrays.sort(keys);

        StringBuilder p = new StringBuilder();
        StringBuilder reqBody = new StringBuilder();
        for (String key : keys) {
            p.append(key).append(params.get(key));
            reqBody.append(key).append("=").append(params.get(key)).append("&");
        }
        p.append(secret);
        
        MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			byte[] bytes = md.digest(p.toString().getBytes("utf-8"));
	        String sign = bytesToHex(bytes);
	        reqBody.append("sign=").append(sign);
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        OkHttpClient client = new OkHttpClient();
		Request request = new Request.Builder()
				  .url(baseUrl + "/open/api/market_dept?"+reqBody.toString())
				  .get()
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();

		try {
			Response res =  client.newCall(request).execute();
			if(res.isSuccessful()) {
				String msg = res.body().string();
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
	
	public static void main(String[] args) {
		String baseUrl = "https://openapi.loex.io/";
		String apiKey = "009cdfcc8476760902a05daf9d842436";
		String secret = "4d2585e4dbccc670d8246b65b8c31db0";
		LoexApiRestClient client = new LoexApiRestClient(baseUrl, apiKey, secret);
		client.getDepth("mcgtusdt");
	}
}
