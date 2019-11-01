package com.kmfrog.martlet.feed.net;

import org.apache.commons.lang3.StringUtils;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.feed.Source;

/**
 * ZeroMQ消息发布服务器。
 * 
 * @author dust Oct 25, 2019
 *
 */
public class FeedBroadcast {

    final ZMQ.Context ctx;
    final ZMQ.Socket publisher;

    public FeedBroadcast(String host, int port, int ioThreads) {
        ctx = ZMQ.context(ioThreads);
        publisher = ctx.socket(SocketType.PUB);
        publisher.bind(String.format("tcp://%s:%d", host, port));

    }

    public void sendDepth(Source src, IOrderBook book, int pricePrecision, int volumePrecision, int maxLevel) {
        // publisher.send(book.getPlainText(src, pricePrecision, volumePrecision, maxLevel));
        publisher.send(book.getOriginText(src, maxLevel));
    }

    public void sendTadeLog(long[] tradeLog) {
        // source, instrument(long), id, price, volume, cnt, isBuy, ts, lastReceived
        publisher.send(StringUtils.join(tradeLog, ","));
    }

    public void destory() {
        publisher.close();
        ctx.close();
    }

}
