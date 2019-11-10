package com.kmfrog.martlet.trade.exec;

import java.util.Set;

import com.kmfrog.martlet.book.TrackBook;

public class CancelRunnable implements Runnable {
    
    private final long createAt;
    private final TrackBook memBook;
    private final Set<Long> orderIds;

    public CancelRunnable(long createAt, Set<Long> orderIds, TrackBook trackBook) {
        this.createAt = createAt;
        this.memBook = trackBook;
        this.orderIds  = orderIds;
    }

    @Override
    public void run() {
        

    }

}
