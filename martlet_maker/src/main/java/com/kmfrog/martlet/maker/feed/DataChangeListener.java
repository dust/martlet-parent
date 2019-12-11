package com.kmfrog.martlet.maker.feed;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.feed.domain.TradeLog;

public interface DataChangeListener {

    /**
     * 实时深度流。
     * @param instrument
     * @param book
     */
    void onDepth(Long instrument, IOrderBook book);

    /**
     * 实时聚合成交流。
     * @param instrument
     * @param tradeLog
     */
    void onTrade(Long instrument, TradeLog tradeLog);

}
