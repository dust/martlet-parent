package com.kmfrog.martlet.book;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PriceLevel {

    private final Side side;
    private final long price;
    private final List<OrderEntry> orders;

    public PriceLevel(Side side, long price) {
        this.side = side;
        this.price = price;
        this.orders = new ArrayList<>();
    }

    public Side getSide() {
        return side;
    }

    public long getPrice() {
        return price;
    }

    public long getSize() {
        return orders.stream().collect(Collectors.summingLong(OrderEntry::getRemainingQuantity));
    }

    /**
     * 获得此价位的订单数。
     * 
     * @return
     */
    public int getCount() {
        return orders.size();
    }

    /**
     * 收缩这个价位的订单，以达到或接近期望值。
     * 
     * @param expect
     * @return 返回被截去的订单id集合。
     */
    public Set<Long> shrinkOrders(long expect) {
        Set<Long> ret = new HashSet<>();
        List<OrderEntry> sortedOrders = orders.stream()
                .sorted(Comparator.comparingLong(OrderEntry::getRemainingQuantity)).collect(Collectors.toList());
        long sum = 0;
        for (OrderEntry entry : sortedOrders) {
            sum += entry.getRemainingQuantity();
            if (sum > expect) {
                ret.add(entry.getId());
            }
        }
        return ret;
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public OrderEntry add(long orderId, long size, int status) {
        OrderEntry order = new OrderEntry(this, orderId, size, status);

        orders.add(order);

        return order;
    }

    void delete(OrderEntry order) {
        orders.remove(order);
    }

    public Set<Long> getOrderIds() {
        return orders.stream().map(ord -> ord.getId()).collect(Collectors.toSet());
    }

    public Set<Long> getOrderIds(int excludeStatus){
        return orders.stream().filter(ord -> ord.getStatus() != excludeStatus).map(ord->ord.getId()).collect(Collectors.toSet());
    }

    @Override
    public String toString() {
        return String.format("{%d: %s}", getSize(), orders.stream()
                .collect(Collectors.toMap(OrderEntry::getId, OrderEntry::getRemainingQuantity)).toString());
    }

}
