package com.kmfrog.martlet.trade;

import java.util.concurrent.Future;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.exec.Exec;

/**
 * 工作线程（主动/驱动对象)与工作台（主要上下文)的沟通桥梁。
 * @author dust Nov 15, 2019
 *
 */
public interface Provider {

    RollingTimeSpan<TradeLog> getRollingTradeLog(Source src, Instrument instrument);

    IOrderBook getOrderBook(Source source, Instrument instrument);
    
    Future<?> submitExec(Exec exec);

    TrackBook getTrackBook(Source src, Instrument instrument);

    void setOrderBook(Source src, Instrument instrument, IOrderBook book);    
    
}
