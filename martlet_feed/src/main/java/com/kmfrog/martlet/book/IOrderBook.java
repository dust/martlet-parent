package com.kmfrog.martlet.book;

import java.io.PrintStream;

import com.kmfrog.martlet.feed.Source;

import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * 订单簿接口。
 * @author dust Sep 30, 2019
 *
 */
public interface IOrderBook extends Comparable<IOrderBook> {

    long getSourceValue();
    long getInstrument();
    
    long getBestBidPrice();
    long getWorstBidPrice();
    LongSortedSet getBidPrices();
    long getBidSize(long price);
    
    long getBestAskPrice();
    long getWorstAskPrice();
    LongSortedSet getAskPrices();
    long getAskSize(long price);
    
    long getLastUpdateTs();
    long getLastReceivedTs();
    void setLastUpdateTs(long ts);
    
    void destroy();
    /**
     * 获得指定数量的价格和档位。
     * @param side
     * @param base
     * @return 数组：[价格，档位]
     */
    long[] getPreferred(Side side, long size);

    /**
     * 以简洁文本平铺整个order book
     * {source},{instrument},{last_update_ts},{last_received_ts},[[price_level1, volume, source;size|source2;size2|...],
     * @param source
     * @param pricePrecision
     * @param volumePrecision
     * @param maxLevel
     * @return
     */
    String getPlainText(Source src, int pricePrecision, int volumePrecision, int maxLevel);
    
    /**
     * 以原始文本平铺整个order book. price, size都是long
     * @param src
     * @param maxLevel
     * @return
     */
    String getOriginText(Source src, int maxLevel);
    
    /**
     * 简洁平铺某一侧的order book.
     * @param side
     * @param pricePrecision
     * @param volumePrecision
     * @param maxLevel
     * @return
     */
    String dumpPlainText(Side side, int pricePrecision, int volumePrecision, int maxLevel);
    
    /**
     * 平铺某一侧的order book原始数据。price,size都为long
     * @param side
     * @param maxLevel
     * @return
     */
    String dumpOriginText(Side side, int maxLevel);
    
    /**
     * 将参数中的变化量累计到订单档位，不存在此档位时会新增。
     * @param side
     * @param price
     * @param delta 变化量，`oldSize` + `delta`小于零时，将会删除此档位。
     * @param source 来源，非聚合订单簿时，此参数并不使用。
     * @return 如果最优档发生变化，返回`true`，否则`false`
     */
    boolean incr(Side side, long price, long delta, int source);
    
    /**
     * 将参数的值直接更新（替换）掉旧档档，不存在此档位时会新增。
     * @param side
     * @param price
     * @param quantity
     * @param source 来源，非聚合订单簿时，此参数并不使用。
     * @return 如果最优档发生变化，返回`true`，否则`false`
     */
    boolean replace(Side side, long price, long quantity, int source);
    
    /**
     * 从订单簿中清理某个来源的订单项。
     * @param side
     * @param source
     * @return
     */
    boolean clear(Side side, int source);
    
    /**
     * 输出某一侧的订单列表。
     * @param side
     * @param ps
     */
    void dump(Side side, PrintStream ps);
}
