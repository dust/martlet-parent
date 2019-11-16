package com.kmfrog.martlet.trade.exec;

import java.util.Set;

import com.kmfrog.martlet.book.TrackBook;

public class CancelExec extends Exec {
    
    private final TrackBook memBook;
    private final Set<Long> orderIds;

    public CancelExec(long createAt, Set<Long> orderIds, TrackBook trackBook) {
        super(createAt);
        this.memBook = trackBook;
        this.orderIds  = orderIds;
    }

    @Override
    public void run() {
        

    }

}
