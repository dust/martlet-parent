package com.kmfrog.martlet.book;

public class OrderEntry {

    private final PriceLevel level;
    private final long id;
    private long remainingQuantity;
    private int status;

    OrderEntry(PriceLevel level, long id, long size, int status) {
        this.level = level;
        this.id = id;
        this.remainingQuantity = size;
        this.status = status;
    }

    public PriceLevel getLevel() {
        return level;
    }

    long getId() {
        return id;
    }
    
    public int getStatus() {
        return status;
    }
    
    public void setStatus(int status) {
        this.status = status;
    }

    public long getRemainingQuantity() {
        return remainingQuantity;
    }
    
    void reduce(long quantity) {
        remainingQuantity -= quantity;
    }
    
    public void resize(long size) {
        remainingQuantity = size;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id ^ (id >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OrderEntry other = (OrderEntry) obj;
        return id == other.getId();
    }

    @Override
    public String toString() {
        return String.format("%d@%d(%d)", remainingQuantity, level.getPrice(), id); 
    }
    
    

}
