package com.kmfrog.martlet.book;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PriceLevel {
    
    private final Side side;
    private final long price;
    private final List<OrderEntry> orders;
    
    PriceLevel(Side side, long price){
        this.side = side;
        this.price = price;
        this.orders = new ArrayList<>();
    }
    
    Side getSide() {
        return side;
    }
    
    long getPrice() {
        return price;
    }
    
    boolean isEmpty() {
        return orders.isEmpty();
    }
    
    OrderEntry add(long orderId, long size) {
        OrderEntry order = new OrderEntry(this, orderId, size);
        
        orders.add(order);
        
        return order;
    }
    
    void delete(OrderEntry order) {
        orders.remove(order);
    }
    
    Set<Long> getOrderIds() {
        return orders.stream().map(ord -> ord.getId()).collect(Collectors.toSet());
    }

}
