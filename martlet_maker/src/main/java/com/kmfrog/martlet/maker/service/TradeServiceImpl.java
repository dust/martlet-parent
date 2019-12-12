package com.kmfrog.martlet.maker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.kmfrog.martlet.maker.api.TatmasRestClient;
import com.kmfrog.martlet.maker.model.entity.Order;

@Service
public class TradeServiceImpl implements TradeService {

    @Autowired
    private TatmasRestClient client;

    public TradeServiceImpl() {
    }

    @Override
    public Long insertOrder(Order order, String api, String secret) {
        return client.limitOrder(order.getSide(), order.getSymbol(), String.valueOf(order.getPrice()),
                String.valueOf(order.getVolume()), api, secret);
    }

}
