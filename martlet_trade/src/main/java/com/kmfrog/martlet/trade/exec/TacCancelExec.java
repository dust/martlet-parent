package com.kmfrog.martlet.trade.exec;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.trade.Provider;

import io.broker.api.client.BrokerApiRestClient;
import io.broker.api.client.domain.account.CancelOrderResponse;
import io.broker.api.client.domain.account.OrderStatus;
import io.broker.api.client.domain.account.request.CancelOrderRequest;

public class TacCancelExec extends Exec {

    static final Logger logger = LoggerFactory.getLogger(TacCancelExec.class);
    private final TrackBook trackBook;
    private final Set<Long> orderIds;
    private final BrokerApiRestClient client;
    private final Provider provider;

    public TacCancelExec(Set<Long> orderIds, BrokerApiRestClient client, Provider provider, TrackBook trackBook) {
        super(System.currentTimeMillis());
        this.client = client;
        this.provider = provider;
        this.trackBook = trackBook;
        this.orderIds = orderIds;
    }

    @Override
    public void run() {
        for (Long orderId : orderIds) {
            try {
            CancelOrderResponse resp = client.cancelOrder(new CancelOrderRequest(orderId));
            logger.info("cancel id:{}, resp: {}", orderId, resp.toString());
            OrderStatus status = resp.getStatus();
            if (status == OrderStatus.CANCELED) {
                trackBook.remove(orderId);
            }
            }
            catch(Exception ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
    }

}
