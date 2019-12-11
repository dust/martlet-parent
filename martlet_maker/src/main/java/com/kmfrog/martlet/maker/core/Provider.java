package com.kmfrog.martlet.maker.core;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.domain.VolumeStrategy;
import com.kmfrog.martlet.maker.exec.Exec;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;

public interface Provider {
	
	/**
     * 获得指定来源及币对的移动均价对象。
     * 
     * @param src
     * @param instrument
     * @return
     */
    RollingTimeSpan<TradeLog> getAvgTrade(Source src, Instrument instrument);
    
    /**
     * 获得指定来源及币对的订单簿。
     * 
     * @param src
     * @param instrument
     * @return
     */
    IOrderBook getOrderBook(Source src, Instrument instrument);
	
    AggregateOrderBook getAggBook(Instrument instrument);

    void setOrderBook(Source src, Instrument instrument, IOrderBook book);

    DepthService getDepthService();

    TradeService getTradeService();

    Future<?> submit(Exec exec);

    TrackBook getTrackBook(Source src, Instrument instrument);

    long getOpenOrderSleepMillis();

    Source getPreferSource(SymbolAoWithFeatureAndExtra symbolInfo);

    int getSpreadLowLimitMillesimal(SymbolAoWithFeatureAndExtra symbolInfo);
    
    long getMakerSleepMillis(SymbolAoWithFeatureAndExtra symbolInfo);

    Source[] getAllSource();

    double getMaxPriceDiff(SymbolAoWithFeatureAndExtra symbolInfo);

    long getMaxDelayMillis(SymbolAoWithFeatureAndExtra symbolInfo);

    Set<String> getSplitTradeSymbols();

    long getSplitTradeMaxDelayMillis(SymbolAoWithFeatureAndExtra symbolInfo);

    double getSplitTradeRatio(SymbolAoWithFeatureAndExtra symbolInfo);

    double getMaxVolumeDiff(Instrument instrument);

    SymbolAoWithFeatureAndExtra getSymbolInfo(Instrument instrument);

    List<VolumeStrategy> getVolumeStrategy(SymbolAoWithFeatureAndExtra symbolInfo);

    int getMaxLevel(SymbolAoWithFeatureAndExtra symbolInfo);
    
    int getBuyRobotId(SymbolAoWithFeatureAndExtra symbolInfo);
    
    int getSellRobotId(SymbolAoWithFeatureAndExtra symbolInfo);
    
    int getMakerTradeUserId(SymbolAoWithFeatureAndExtra symbolInfo);

}
