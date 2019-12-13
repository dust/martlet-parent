package com.kmfrog.martlet.maker.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.maker.api.TatmasRestClient;
import com.kmfrog.martlet.maker.model.entity.Order;

@Service
public class DepthServiceImpl implements DepthService {

    private Logger logger = LoggerFactory.getLogger(DepthServiceImpl.class);

    @Autowired
    private TatmasRestClient client;

    public DepthServiceImpl() {
    }

    @Override
    public Integer cancelOpenOrder(Long orderId, String api, String secret) {
        boolean isSuccess = client.cancelOrder(orderId, api, secret);
        if (isSuccess) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public Long insertOrder(Order order, String api, String secret) {
        return client.limitOrder(order.getSide(), order.getSymbol(), String.valueOf(order.getPrice()),
                String.valueOf(order.getVolume()), api, secret);
    }

    @Override
    public List<Order> getOpenOrders(String symbol, Side side, Integer userId, String api, String secret) {
        int pageNo = 1;
        int pageSize = 1000;
        String text = client.getOpenOrder(symbol, side, pageNo, pageSize, api, secret);
        List<Order> ret = new ArrayList<Order>();
        DefaultJSONParser parser = new DefaultJSONParser(text);

        try {
            JSONObject data = parser.parseObject();
            if (data != null) {
                JSONArray content = data.getJSONArray("content");
                for (Object o : content) {
                    JSONObject orderJson = (JSONObject) o;
                    Side sideObj = orderJson.getString("direction").equals("BUY") ? Side.BUY : Side.SELL;
                    BigDecimal price = orderJson.getBigDecimal("price");
                    BigDecimal amount = orderJson.getBigDecimal("amount");
                    Order order = Order.buildOrderByPriceLevel(orderJson.getString("symbol"), sideObj, price, amount,
                            userId);
                    order.setId(orderJson.getLongValue("orderId"));
                    order.setDealVolume(orderJson.getBigDecimal("tradedAmount"));
                    order.setStatus(1);
                    ret.add(order);
                }
            } else {
                logger.warn("getOpenOrders:{},{},{}", symbol, side, text);
            }
        } catch (Exception ex) {
            logger.warn("{}-{}", text, ex.getMessage(), ex);
        }
        finally {
            parser.close();
        }

        return ret;
    }

}
