package com.kmfrog.martlet.feed;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

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
                    if(System.currentTimeMillis() % 100 == 0) {
                    System.out.println(str);
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
