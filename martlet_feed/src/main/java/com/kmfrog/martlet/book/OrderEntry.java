package com.kmfrog.martlet.book;

public class OrderEntry {

    private final PriceLevel level;
    private final long id;
    private long remainingQuantity;

    OrderEntry(PriceLevel level, long id, long size) {
        this.level = level;
        this.id = id;
        this.remainingQuantity = size;
    }

    public PriceLevel getLevel() {
        return level;
    }

    long getId() {
        return id;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }
    
    void reduce(long quantity) {
        remainingQuantity -= quantity;
    }
    
    void resize(long size) {
        remainingQuantity = size;
    }

}
