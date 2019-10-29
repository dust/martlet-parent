package com.kmfrog.martlet.book;

/**
 * 多源用户订单（存在订单id, 唯一)
 * @author dust Oct 8, 2019
 *
 */
public class MultiSrcOrder {
    
    /**
     * 用户订单持有所属订单表的引用。
     */
    private final AggregateOrderBook book;

    private final Side side;
    private final long price;
    /**
     * 订单源。
     */
    private final int source;

    /**
     * 用户订单的剩余数量。
     */
    private long remainingQuantity;

    /**
     * 多源用户订单（有唯一订单id）
     * @param book
     * @param side
     * @param price
     * @param size
     * @param source
     */
    MultiSrcOrder(AggregateOrderBook book, Side side, long price, long size, int source) {
        this.book = book;
        
        this.side = side;
        this.price = price;
        this.source = source;

        this.remainingQuantity = size;
    }
    
    public AggregateOrderBook getOrderBook() {
        return book;
    }
    
    public long getPrice() {
        return price;
    }
    
    public Side getSide() {
        return side;
    }
    
    public int getSource() {
        return source;
    }
    
    /**
     * Get the remaining quantity.
     *
     * @return the remaining quantity
     */
    public long getRemainingQuantity() {
        return remainingQuantity;
    }

    /**
     * 重设交易数量。比如修改订单数量。 如果来源与实例来源不匹配，将不会执行任何操作。
     * @param remainingQuantity 剩余数量
     * @param source  来源。
     */
    void setRemainingQuantity(long remainingQuantity, int source) {
        if(source == this.source) {
            this.remainingQuantity = remainingQuantity;
        }
    }

    /**
     * 削减数量，比如部分成交。
     * @param quantity
     * @param source 来源。
     */
    void reduce(long quantity, int source) {
        if (source == this.source) {
            remainingQuantity -= quantity;
        }
    }

}
