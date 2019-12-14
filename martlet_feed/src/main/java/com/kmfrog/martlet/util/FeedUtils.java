package com.kmfrog.martlet.util;

import static com.kmfrog.martlet.C.SECOND_SEPARATOR;
import static com.kmfrog.martlet.C.SEPARATOR;
import static com.kmfrog.martlet.C.THIRD_SEPARATOR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

public class FeedUtils {

    static Logger logger = LoggerFactory.getLogger(FeedUtils.class);

    public static TradeLog parseZMQTrade(String originText) {
        TradeLog log = null;
        try {
            String[] strArray = StringUtils.split(originText, SEPARATOR);
            long[] fields = Arrays.stream(strArray).mapToLong(s -> Long.valueOf(s)).toArray();
            if (fields.length >= 9) {
                return new TradeLog(fields);
            }

        } catch (Exception ex) {
            logger.warn("parse exception: {}, {}", ex.getMessage(), originText);
        }
        return log;
    }

    public static IOrderBook parseZMQDepth(String originText) {
        IOrderBook book = null;
        try {
            JSONArray depth = JSONArray.parseArray(originText);
            if (depth.size() == 6) {
                int src = depth.getIntValue(0);
                long instrument = depth.getLongValue(1);
                long updateTs = depth.getLongValue(2);
                // long recvTs = depth.getLongValue(3);
                if (src == Source.Mix.ordinal()) {
                    book = new AggregateOrderBook(instrument);

                    JSONArray bids = depth.getJSONArray(4);
                    JSONArray asks = depth.getJSONArray(5);
                    updatePriceLevel(book, Side.BUY, bids);
                    updatePriceLevel(book, Side.SELL, asks);
                } else {
                    book = new OrderBook(Source.values()[src], instrument);

                    JSONArray bids = depth.getJSONArray(4);
                    JSONArray asks = depth.getJSONArray(5);
                    updatePriceLevel(book, Side.BUY, bids);
                    updatePriceLevel(book, Side.SELL, asks);
                }
                book.setLastUpdateTs(updateTs);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {

        }

        return book;
    }

    public static List<Instrument> parseInstruments(String json) {
        List<Instrument> lst = new ArrayList<>();
        try {
            JSONArray arr = JSONArray.parseArray(json);
            int len = arr.size();
            for (int i = 0; i < len; i++) {
                JSONObject o = arr.getJSONObject(i);
                int orderPriceFractionDigits = o.getIntValue("showPrice");
                lst.add(new Instrument(o.getString("name").toUpperCase(), o.getIntValue("p"), o.getIntValue("v"), orderPriceFractionDigits));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return lst;
    }

    public static Set<String> parseInstrumentName(String json) {
        Set<String> keys = new HashSet<>();
        try {
            JSONArray arr = JSONArray.parseArray(json);
            int len = arr.size();
            for (int i = 0; i < len; i++) {
                keys.add(arr.getString(i));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return keys;
    }

    public static Map<String, Object> parseConfigArgs(String json) {
        Map<String, Object> cfg = new HashMap<>();
        DefaultJSONParser parser = new DefaultJSONParser(json);

        try {
            JSONObject root = parser.parseObject();
            Set<String> keys = root.keySet();
            for (String key : keys) {
                Map<String, String> map = new HashMap<>();
                JSONObject child = root.getJSONObject(key);
                Set<String> childKeys = child.keySet();
                for (String ck : childKeys) {
                    map.put(ck, child.getString(ck));
                }
                cfg.put(key, map);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            parser.close();
        }
        return cfg;

    }

    private static void updatePriceLevel(IOrderBook book, Side side, JSONArray arr) {
        int size = arr.size();
        for (int i = 0; i < size; i++) {
            JSONArray item = arr.getJSONArray(i);
            long price = item.getLongValue(0);
            int sourceValue = (int) book.getSourceValue();
            if (sourceValue > Source.Mix.ordinal()) {
                long quantity = item.getLongValue(1);
                book.replace(side, price, quantity, sourceValue);
            } else {
                String[] multiSrc = StringUtils.split(item.getString(2), THIRD_SEPARATOR);
                for (int n = 0; n < multiSrc.length; n++) {
                    String[] srcSizePair = StringUtils.split(multiSrc[n], SECOND_SEPARATOR);
                    int source = Integer.parseInt(srcSizePair[0]);
                    long quantity = Long.parseLong(srcSizePair[1]);
                    book.replace(side, price, quantity, source);
                }
            }
        }
    }

    public static int between(int min, int max) {
        Random rnd = new Random(System.currentTimeMillis());
        return (int) (min + rnd.nextDouble() * (max - min));
    }

    /**
     * 获得随机数。两边都不包含。
     * 
     * @param min
     * @param max
     * @return
     */
    public static long between(long min, long max) {
        Random rnd = new Random(System.currentTimeMillis());
        return (long) (min + rnd.nextDouble() * (max - min));
    }
    
    public static long between(long min, long max, double spread) {
    	Random rnd = new Random(System.currentTimeMillis());
    	return (long) (min + rnd.nextDouble() * (max - min) * spread);
    }

    public static void main(String[] args) {
        String originText = "[0,4779519138295206944,1572598396807,1572598397029,[[912716000000,5473100,\"3;5473100\"],[912710000000,694494730,\"2;694494730\"],[912707000000,227235000,\"3;227235000\"],[912700000000,3499999,\"2;3499999\"],[912680000000,2164413,\"2;2164413\"]],[[912720000000,42346318,\"2;42346318\"],[912727000000,2715900,\"3;2715900\"],[912795000000,1000,\"1;1000\"],[912820000000,219129,\"2;219129\"],[912970000000,51110000,\"3;51110000\"]]]";
        Source expectSrc = Source.values()[0];
        IOrderBook book = parseZMQDepth(originText);
        System.out.println(book.getOriginText(expectSrc, 5));
    }
}
