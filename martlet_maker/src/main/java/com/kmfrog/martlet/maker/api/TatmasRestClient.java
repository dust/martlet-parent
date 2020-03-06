package com.kmfrog.martlet.maker.api;

import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.kmfrog.martlet.book.Side;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Component
public class TatmasRestClient {

    static Logger logger = LoggerFactory.getLogger(TatmasRestClient.class);

//    @Autowired
//    SpringContext springContext;
    
//    private String apiKey;
//    private String secret;
    private final String signatureFmt = "AccessKeyId=%s&SignatureMethod=HmacSHA256&SignatureVersion=1&Timestamp=%s";
    private final OkHttpClient client;
    

    public TatmasRestClient(/* String apiKey, String secret */) {
//        this.apiKey = apiKey;
//        this.secret = secret;
       
        client = new OkHttpClient();
    }

    /**
     * 查询委托成交明细
     * 
     * @param orderId
     * @return
     */
    public String getOrderDetail(Long orderId, String api, String secret) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("orderId", String.valueOf(orderId));
        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/exchange/order/open/detail", api, secret, params);
            if (res.isSuccessful()) {
            	return res.body().string();
            }
        } catch (Exception ex) {
            logger.warn("getOrderDetail exception {}: {}", ex.getMessage(), orderId);
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
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
     *         {"content":[{"orderId":"E157598654446490","memberId":6148,"type":"LIMIT_PRICE","amount":0.00100000,"symbol":"BTC/USDT","tradedAmount":0E-16,"turnover":0E-16,"coinSymbol":"BTC","baseSymbol":"USDT","status":"CANCELED","direction":"BUY","price":5000.00000000,"time":1575986544464,"completedTime":null,"canceledTime":1575987356767,"useDiscount":"0","detail":[],"completed":true}],"last":true,"totalElements":1,"totalPages":1,"first":true,"sort":[{"direction":"DESC","property":"time","ignoreCase":false,"nullHandling":"NATIVE","ascending":false,"descending":true}],"numberOfElements":1,"size":10,"number":0}
     */
    public String getHistoryOrder(String symbol, long startTime, long endTime, Side side, int status, int pageNo,
            int pageSize, String api, String secret) {
        Map<String, String> params = new HashMap<String, String>();
        String direction = side == Side.BUY ? "0" : "1";
        params.put("type", "1");
        params.put("symbol", getTatmasSymbol(symbol.toUpperCase()));
        params.put("startTime", String.valueOf(startTime));
        params.put("endTime", String.valueOf(endTime));
        params.put("direction", direction);
        params.put("status", String.valueOf(status));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/exchange/order/open/personal/history", api, secret, params);
            if (res.isSuccessful()) {
                return res.body().string();
            }
        } catch (Exception ex) {
            logger.warn(" getHistoryOrder exception {}: {} {} {} {} {} {} {}", ex.getMessage(), symbol,
                    String.valueOf(startTime), String.valueOf(endTime), direction, status, pageNo, pageSize);
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
        }

        return null;
    }

    public boolean cancelOrder(Long orderId, String api, String secret) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("orderId", String.valueOf(orderId));
        
        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/exchange/order/open/cancel", api, secret, params);
            if (res.isSuccessful()) {
                JSONObject data = JSONObject.parseObject(res.body().string());
                if (data.containsKey("code") && data.getInteger("code") != 0) {
//                    logger.warn(" cancelOrder failed {}: {}", data.getString("message"), orderId);
                    return false;
                }
                return true;
            }
        } catch (Exception ex) {
//            logger.warn(" cancelOrder exception {}: {}", ex.getMessage(), orderId);
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
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
    public String getOpenOrder(String symbol, Side side, int pageNo, int pageSize, String api, String secret) {
        Map<String, String> params = new HashMap<String, String>();
        String direction = side == Side.BUY ? "0" : "1";
        params.put("type", "1");
        params.put("symbol", getTatmasSymbol(symbol.toUpperCase()));
        // params.put("startTime", String.valueOf(startTime));
        params.put("startTime", "");
        // params.put("endTime", String.valueOf(endTime));
        params.put("endTime", "");
        params.put("direction", direction);
        params.put("pageNo", String.valueOf(pageNo));
        params.put("pageSize", String.valueOf(pageSize));
        
        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/exchange/order/open/personal/current", api, secret, params);
            String respText=null;
            if(res.isSuccessful()) {
                respText = res.body().string();
            }
//            logger.info(String.format("%s|%s", params, respText));
            return respText;
        } catch (Exception ex) {
            logger.warn(" getOpenOrder exception {}: {} {} {} {}", ex.getMessage(), symbol, direction, pageNo,
                    pageSize);
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
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
    public Long limitBuy(String symbol, String price, String amount, String api, String secret) {
        return limitOrder(Side.BUY, symbol, price, amount, api, secret);
    }

    /**
     * 
     * @param symbol
     * @param price
     * @param amount
     * @return 订单号 ""
     */
    public Long limitSell(String symbol, String price, String amount, String api, String secret) {
        return limitOrder(Side.SELL, symbol, price, amount, api, secret);
    }

    /**
     * 
     * @param side
     * @param symbol
     * @param price
     * @param amount
     * @return
     * 
     *         {"code":0,"message":"success","totalPage":null,"totalElement":null,"data":"E157602986649923"}
     */
    public Long limitOrder(Side side, String symbol, String price, String amount, String apiKey, String secretKey) {
        Map<String, String> params = new HashMap<String, String>();
        String direction = side == Side.BUY ? "0" : "1";
        params.put("type", "1"); // 0,市价 1,限价
        params.put("direction", direction);
        params.put("symbol", getTatmasSymbol(symbol.toUpperCase()));
        params.put("price", price);
        params.put("amount", amount);

        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/exchange/order/open/add", apiKey, secretKey, params);
            if (res.isSuccessful()) {
                String str = res.body().string();
                JSONObject data = JSONObject.parseObject(str);
                return data.getLong("data");
            }
        } catch (Exception ex) {
            logger.warn(" limitOrder exception {}: {} {} {} {}", direction, symbol, price, amount, ex.getMessage());
            ex.printStackTrace();
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
        }
        return null;
    }

    /**
     * 
     * @param symbol
     * @return {"ask":{"minAmount":9.99500000,"highestPrice":7403.37380000,"symbol":"BTC/USDT","lowestPrice":7403.37380000,"maxAmount":9.99500000,"items":[{"amount":9.99500000,"price":7403.37380000}],"direction":"SELL"},"bid":{"minAmount":0.00100000,"highestPrice":7143.70910000,"symbol":"BTC/USDT","lowestPrice":5000.00000000,"maxAmount":0.50000000,"items":[{"amount":0.50000000,"price":7143.70910000},{"amount":0.00100000,"price":5000.00000000}],"direction":"BUY"}}
     */
    public String getDepth(String symbol, String apiKey, String secretKey) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("symbol", getTatmasSymbol(symbol.toUpperCase()));
        Response res = null;
        try {
            res = doPost("https://tatmasglobal.net/market/open/exchange-plate-full", apiKey, secretKey, params);
            if (res.isSuccessful()) {
                return res.body().string();
            }
        } catch (Exception ex) {
            logger.warn(" getDepth exception {}: {}", ex.getMessage(), symbol);
        } finally {
        	if(res != null) {
        		res.body().close();
        	}
        }
        return null;
    }

    private Response doPost(String url, String apiKey, String secretKey, Map<String, String> params) throws Exception {
//        String data = String.format(this.signatureFmt, apiKey, System.currentTimeMillis());
//        String signature = URLEncoder.encode(TatmasRestClient.HMACSHA256(data, secret), "UTF-8");
      String data = String.format(signatureFmt, apiKey, System.currentTimeMillis());
      String signature = URLEncoder.encode(TatmasRestClient.HMACSHA256(data, secretKey), "UTF-8");

        StringBuilder reqBody = new StringBuilder();
        reqBody.append(data).append("&Signature=").append(signature);

        String[] keys = new String[params.size()];
        params.keySet().toArray(keys);
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            reqBody.append("&").append(key).append("=").append(params.get(key));
        }

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, reqBody.toString());
        Request request = new Request.Builder().url(url).post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded").build();
        return client.newCall(request).execute();
    }

    /**
     * 生成 HMACSHA256
     * 
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
        } catch (Exception ex) {

        }
        return "";
    }

    public String getTatmasSymbol(String symbol) {
        if (symbol.endsWith("USDT")) {
            // BTCUSDT -> 0, smiles(1,5)
            return String.format("%s/%s", symbol.substring(0, symbol.length() - 4),
                    symbol.substring(symbol.length() - 4));
        }
        return String.format("%s/%s", symbol.substring(0, symbol.length() - 3),
                symbol.substring(symbol.length() - 3));
    }

    public static void main(String[] args) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long startTime = format.parse("2019-12-01 00:00:00").getTime();
            long endTime = format.parse("2019-12-30 00:00:00").getTime();
            
            TatmasRestClient client = new TatmasRestClient(/*apiKey, secret*/);
            
//            System.out.println(client.limitBuy("ETHUSDT", "148", "0.001", apiKey, secret));
//            System.out.println(client.limitSell("ETHUSDT", "148", "0.001", apiKey, secret));
            
//             System.out.println(client.getDepth("ETHUSDT", apiKey, secret));
//             System.out.println(client.limitBuy("ETHUSDT", "105.1", "0.001", apiKey, secret));
            // System.out.println(client.cancelOrder("E157602986649923"));
//             System.out.println(client.getOrderDetail(654695783345422336l, apiKey, secret));

//             System.out.println(client.getHistoryOrder("ETHUSDT", startTime, endTime, Side.BUY, 2, 1, 10, apiKey, secret));
//             System.out.println(client.getOpenOrder("ETHUSDT", Side.BUY, 1, 10, apiKey, secret));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
