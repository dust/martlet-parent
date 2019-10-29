package com.kmfrog.martlet.feed;

import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.Side;

public class AggregateRunnable implements Runnable {

    private OrderBook book;
    private Source source;
    private App app;
    private Action act;

    AggregateRunnable(Source src, Action act, OrderBook single, App app) {
        this.book = single;
        this.source = src;
        this.app = app;
        this.act = act;
    }
    
    @Override
    public void run() {
//        long b = System.currentTimeMillis();
//        AggregateOrderBook aggBook = app.makesureAggregateOrderBook(book.getInstrument());
            // TODO: 检查不同来源的order book的边界偏离程度。 diff time, diff bbo
//        if(act == Action.REPLACE || act == Action.INCREMENT) {
//            aggBook.aggregate(source, book);
//        }
//        else if(act == Action.CLEAR) {
//            aggBook.clear(Side.BUY, source.ordinal());
//            aggBook.clear(Side.SELL, source.ordinal());
//        }
//        System.out.println("\nagg:"+(System.currentTimeMillis() - b));
    }

}
