package com.kmfrog.martlet.trade.bikun;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Instrument;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BikunApiRestClient {
	static Logger logger = LoggerFactory.getLogger(BikunApiRestClient.class);
	
	private final String baseUrl;
	private final String apiKey;
	private final String secret;
	
	public BikunApiRestClient(String baseUrl, String apiKey, String secret) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.secret = secret;
	}
	
	public Long limitBuy(Instrument instrument, String quantityStr, String priceStr) {
		String symbol = instrument.asString().equals("AICUSDT") ? "aic1usdt" : instrument.asString().toLowerCase();
		Response res = this.limitOrder("BUY", symbol, quantityStr, priceStr);
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
		String symbol = instrument.asString().equals("AICUSDT") ? "aic1usdt" : instrument.asString().toLowerCase();
		Response res = this.limitOrder("SELL", symbol, quantityStr, priceStr);
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
		/** 封装需要签名的参数 */
		long time = new Date().getTime();
		TreeMap<String, String> params = new TreeMap<String, String>();
		params.put("side", side);
		params.put("type", "1"); //1限价委托　2市价委托
		params.put("volume", volume);
		params.put("price", price);
		params.put("symbol", symbol);
		params.put("api_key", apiKey);
		params.put("time", time+"");
		
		/** 拼接签名字符串，md5签名 */
        StringBuilder result = new StringBuilder();
        StringBuilder reqBody = new StringBuilder();
        Set<Entry<String, String>> entrys = params.entrySet();
        for (Entry<String, String> param : entrys) {
            /** 去掉签名字段 */
            if(param.getKey().equals("sign")){
                continue;
            }
            /** 空参数不参与签名 */
            if(param.getValue()!=null) {
                result.append(param.getKey());
                result.append(param.getValue().toString());
            }
            reqBody.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }
        result.append(secret);
        String sign = getMD5(result.toString());
        reqBody.append("sign=").append(sign);
		
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
	
	/**
     * 获取String的MD5值
     *
     * @param info 字符串
     * @return 该字符串的MD5值
     */
    public static String getMD5(String info) {
        try {
        	//获取 MessageDigest 对象，参数为 MD5 字符串，表示这是一个 MD5 算法（其他还有 SHA1 算法等）：
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            //update(byte[])方法，输入原数据
            //类似StringBuilder对象的append()方法，追加模式，属于一个累计更改的过程
            md5.update(info.getBytes("UTF-8"));
            //digest()被调用后,MessageDigest对象就被重置，即不能连续再次调用该方法计算原数据的MD5值。可以手动调用reset()方法重置输入源。
            //digest()返回值16位长度的哈希值，由byte[]承接
            byte[] md5Array = md5.digest();
            //byte[]通常我们会转化为十六进制的32位长度的字符串来使用,本文会介绍三种常用的转换方法
            return bytesToHex(md5Array);
        } catch (NoSuchAlgorithmException e) {
            return "";
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }
 
    private static String bytesToHex(byte[] md5Array) {
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < md5Array.length; i++) {
            int temp = 0xff & md5Array[i];
            String hexString = Integer.toHexString(temp);
            if (hexString.length() == 1) {//如果是十六进制的0f，默认只显示f，此时要补上0
                strBuilder.append("0").append(hexString);
            } else {
                strBuilder.append(hexString);
            }
        }
        return strBuilder.toString();
    }
    
    public JSONObject getOpenOrder(String symbol, String page, String pageSize) {
    	/** 封装需要签名的参数 */
		long time = new Date().getTime();
		TreeMap<String, String> params = new TreeMap<String, String>();
		params.put("symbol", symbol);
		params.put("page", page);
		params.put("pageSize", pageSize);
		params.put("api_key", apiKey);
		params.put("time", time+"");
		
		/** 拼接签名字符串，md5签名 */
        StringBuilder result = new StringBuilder();
        StringBuilder reqBody = new StringBuilder();
        Set<Entry<String, String>> entrys = params.entrySet();
        for (Entry<String, String> param : entrys) {
            /** 去掉签名字段 */
            if(param.getKey().equals("sign")){
                continue;
            }
            /** 空参数不参与签名 */
            if(param.getValue()!=null) {
                result.append(param.getKey());
                result.append(param.getValue().toString());
            }
            reqBody.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }
        result.append(secret);
        String sign = getMD5(result.toString());
        reqBody.append("sign=").append(sign);
		
		OkHttpClient client = new OkHttpClient();
		System.out.println(baseUrl + "/open/api/v2/new_order?"+reqBody.toString());
		Request request = new Request.Builder()
				  .url(baseUrl + "/open/api/new_order?"+reqBody.toString())
				  .get()
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();

		try {
			Response res = client.newCall(request).execute();
			System.out.println(res.body().string());
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			
		}
		return null;
    }
    
    public void cancelOrder(String orderId, String symbol) {
    	/** 封装需要签名的参数 */
		long time = new Date().getTime();
		TreeMap<String, String> params = new TreeMap<String, String>();
		params.put("order_id", orderId);
		params.put("symbol", symbol);
		params.put("api_key", apiKey);
		params.put("time", time+"");
		
		/** 拼接签名字符串，md5签名 */
        StringBuilder result = new StringBuilder();
        StringBuilder reqBody = new StringBuilder();
        Set<Entry<String, String>> entrys = params.entrySet();
        for (Entry<String, String> param : entrys) {
            /** 去掉签名字段 */
            if(param.getKey().equals("sign")){
                continue;
            }
            /** 空参数不参与签名 */
            if(param.getValue()!=null) {
                result.append(param.getKey());
                result.append(param.getValue().toString());
            }
            reqBody.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }
        result.append(secret);
        String sign = getMD5(result.toString());
        reqBody.append("sign=").append(sign);
		
		OkHttpClient client = new OkHttpClient();
		MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
		RequestBody body = RequestBody.create(mediaType, reqBody.toString());
		Request request = new Request.Builder()
				  .url(baseUrl + "/open/api/cancel_order")
				  .post(body)
				  .addHeader("Content-Type", "application/x-www-form-urlencoded")
				  .build();

		try {
			Response resp = client.newCall(request).execute();
			System.out.println(resp.body().string());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			
		}
    }
    
    public static void main(String[] args) {
//    	String baseUrl = "https://www.bikun.io/exchange-open-api";
    	String baseUrl = "https://openapi.bikun.io";
    	String apiKey = "c31b2d41d6e70f4f8103767b284071fb";
    	String secret = "3f2d5692476c0c7e9541e50172eeebb8";
    	BikunApiRestClient client = new BikunApiRestClient(baseUrl, apiKey, secret);
//    	client.getOpenOrder("aic1usdt", "1", "100");
    	client.cancelOrder("96766", "aic1usdt");
    	client.cancelOrder("96769", "aic1usdt");
    }

}
