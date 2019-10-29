package com.kmfrog.martlet.feed.net;

import java.nio.ByteBuffer;

/**
 * Common definitions.
 */
public class PMBK {

    private PMBK() {
    }

    public static final long VERSION = 2;

    public static final byte BUY = 'B';
    public static final byte SELL = 'S';

    static final byte MESSAGE_TYPE_VERSION = 'V';
    static final byte MESSAGE_TYPE_ORDER_SNAPSHOT = 'K';

    static final int MESSAGE_LENGTH_VERSION = 5;

    /**
     * A message.
     */
    public interface Message extends ProtocolMessage {
    }

    /**
     * A Version message.
     */
    public static class Version implements Message {
        public long version;

        @Override
        public void get(ByteBuffer buffer) {
            version = getUnsignedInt(buffer);
        }

        @Override
        public void put(ByteBuffer buffer) {
            buffer.put(MESSAGE_TYPE_VERSION);
            putUnsignedInt(buffer, version);
        }
    }

    /**
     * 来源的最后更新时间。
     * @author dust Oct 16, 2019
     *
     */
    public static class SourceTimestamp implements Message {

        public long source;
        public long timestamp;

        @Override
        public void get(ByteBuffer buf) {
            source = getUnsignedInt(buf);
            timestamp = getUnsignedInt(buf);
        }

        @Override
        public void put(ByteBuffer buf) {
            putUnsignedInt(buf, source);
            putUnsignedInt(buf, timestamp);

        }

    }

    /**
     * 多源订单项。
     * @author dust Oct 16, 2019
     *
     */
    public static class Order implements Message {

        public long price;
        public long quantity;
        public long source;

        @Override
        public void get(ByteBuffer buf) {
            price = getUnsignedInt(buf);
            quantity = getUnsignedInt(buf);
            source = getUnsignedInt(buf);

        }

        @Override
        public void put(ByteBuffer buf) {
            putUnsignedInt(buf, price);
            putUnsignedInt(buf, quantity);
            putUnsignedInt(buf, source);
        }

    }

    /**
     * 
     * @author dust Oct 16, 2019
     *
     */
    public static class OrderBook implements Message {
        public long instrument;
        /**
         * 最后聚合时间。
         */
        public long localTs;
        /**
         * 各来源的最后更新时间。
         */
        public SourceTimestamp[] srcTs;
        public Order[] bids;
        public Order[] asks;

        @Override
        public void get(ByteBuffer buf) {
            localTs = getUnsignedInt(buf);

            int size = (int) getUnsignedInt(buf);
            srcTs = new SourceTimestamp[size];
            for (int i = 0; i < size; i++) {
                srcTs[i] = new SourceTimestamp();
                srcTs[i].get(buf);
            }

            byte side = buf.get();
            if (side == BUY) {
                bids = getOrders(buf);
            } else if (side == SELL) {
                asks = getOrders(buf);
            }

        }

        @Override
        public void put(ByteBuffer buf) {
            putUnsignedInt(buf, localTs);

            putUnsignedInt(buf, srcTs.length);
            for (int i = 0; i < srcTs.length; i++) {
                srcTs[i].put(buf);
            }

            buf.put(BUY);
            putUnsignedInt(buf, bids.length);
            for (int i = 0; i < bids.length; i++) {
                bids[i].put(buf);
            }

            buf.put(SELL);
            putUnsignedInt(buf, asks.length);
            for (int i = 0; i < asks.length; i++) {
                asks[i].put(buf);
            }

        }

    }

    private static Order[] getOrders(ByteBuffer buf) {
        int size = (int) getUnsignedInt(buf);
        Order[] orders = new Order[size];

        for (int i = 0; i < size; i++) {
            orders[i] = new Order();
            orders[i].get(buf);
        }

        return orders;

    }

    private static long getUnsignedInt(ByteBuffer buffer) {
        return buffer.getInt() & 0xffffffffL;
    }

    private static void putUnsignedInt(ByteBuffer buffer, long value) {
        buffer.putInt((int) value);
    }

}
