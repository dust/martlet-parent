package com.kmfrog.martlet.book;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.util.Fmt;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

/**
 * an item in multi-source order book. 多源订单项。
 * 
 * @author dust Sep 30, 2019
 *
 */
public class MultiSrc {

    /**
     * 当前订单的价格。（冗余)
     */
    private final long price;
    /**
     * 多路来源及其size的集合。
     */
    private final Int2LongOpenHashMap srcSizeMap;

    /**
     * 某价位下，所有来源的数量集合。
     * 
     * @param price
     */
    MultiSrc(long price) {
        this.price = price;
        this.srcSizeMap = new Int2LongOpenHashMap();
    }

    long size() {
        return srcSizeMap.values().stream().mapToLong(v -> v.longValue()).sum();
    }

    /**
     * 增加一个增量到指定的来源。Adds an increment to value currently associated with a key.
     * 
     * @param quantity increment quantity.
     * @param source
     * @return the new value
     */
    long addTo(long quantity, int source) {
        srcSizeMap.addTo(source, quantity);
        return size();
    }

    /**
     * 直接更新指定来源到指定的数量。
     * 
     * @param quantity
     * @param source
     * @return 更新后，全部来源的数量累计和。
     */
    long updateTo(long quantity, int source) {
        if (quantity == 0) {
            srcSizeMap.remove(source);
        } else {
            srcSizeMap.put(source, quantity);
        }
        return size();
    }

    /**
     * 清理某个来源的数量。
     * 
     * @param source
     */
    boolean clearSource(int source) {
        if (srcSizeMap.containsKey(source)) {
            srcSizeMap.remove(source);
        }
        return size() == 0;
    }

    public String dumpPlainText(int volumePrecision) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        srcSizeMap.forEach((k, v) -> {
            if (sb.length() > 1) {  // "
                sb.append(C.THIRD_SEPARATOR);
            }
            sb.append(k).append(C.SECOND_SEPARATOR).append(Fmt.fmtNum(v, volumePrecision));
        });
        sb.append('"');
        return sb.toString();
    }
    
    public String dumpOriginText() {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        srcSizeMap.forEach((k, v) -> {
            if (sb.length() > 1) {  // "
                sb.append(C.THIRD_SEPARATOR);
            }
            sb.append(k).append(C.SECOND_SEPARATOR).append(v);
        });
        sb.append('"');
        return sb.toString();
    }

    String dumpString() {
        return srcSizeMap.toString();
    }

}
