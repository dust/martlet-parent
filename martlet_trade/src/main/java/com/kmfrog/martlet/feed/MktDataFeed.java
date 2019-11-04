package com.kmfrog.martlet.feed;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.util.FeedUtils;

/**
 * 
 * @author dust Oct 28, 2019
 *
 */
public class MktDataFeed extends Thread{
    
    final Logger logger = LoggerFactory.getLogger(MktDataFeed.class);
    
    final ZMQ.Context ctx;
    final String address;
    ZMQ.Socket subscriber;
    AtomicBoolean isQuit;
    
    public MktDataFeed(String host, int port, int threads) {
        isQuit = new AtomicBoolean(false);
        ctx = ZMQ.context(threads);
        address = String.format("tcp://%s:%d", host, port);
        
    }
    
    public void run() {
        try {
            subscriber = ctx.socket(SocketType.SUB);
            subscriber.connect(address);
            subscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
            
            while(!isQuit.get()) {
                try {
                    String str = subscriber.recvStr();
                    IOrderBook book = FeedUtils.parseZMQDepth(str);
                    if(System.currentTimeMillis() % 100 == 1) {
                    System.out.println(book.getOriginText(Source.Mix, 5));
                    }
                }
                catch(ZMQException zex) {
                    logger.warn("");
                }
                
            }
        }
        catch(Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
    }
    
    
    
    

}
