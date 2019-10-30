package com.kmfrog.martlet.feed;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;

/**
 * 业务控制器接口。 用于将内部状态传导至外部控制者。也用于内部调用顶层接口。
 * 
 * @author dust Oct 11, 2019
 *
 */
public interface Controller {

    /**
     * 重置某个来源
     * 
     * @param mkt
     * @param instrument
     * @param depth
     * @param resubscribe
     * @param reconnect
     */
    void reset(Source mkt, Instrument instrument, BaseInstrumentDepth depth, boolean resubscribe, boolean reconnect);
    
    /**
     * 清除然后更新深度数据。
     * @param mkt
     * @param instrument
     * @param book 如果book为null, 则只清理某来源的订单项。
     */
    void resetBook(Source mkt, Instrument instrument, IOrderBook book);

    /**
     * 反馈偏离程度
     * @param source
     * @param instrument
     * @param book
     * @param bestBid
     * @param bestAsk
     * @param lastUpdate
     * @param lastReceived
     */
    void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk, long lastUpdate,
            long lastReceived);

    /**
     * 
     * 记录trade流日志。
     * @param src
     * @param instrument
     * @param id  流水id
     * @param price 流水价格
     * @param volume
     * @param cnt
     * @param isBuy
     * @param ts
     */
    void logTrade(Source src, Instrument instrument, long id, long price, long volume, long cnt, long isBuy, long ts);

}
