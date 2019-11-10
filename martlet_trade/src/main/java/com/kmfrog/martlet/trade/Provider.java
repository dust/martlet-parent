package com.kmfrog.martlet.trade;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;

public interface Provider {

    RollingTimeSpan<TradeLog> getAvgTrade(Source src, Instrument instrument);
    
}
