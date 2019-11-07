package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DepthFeed;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.TradeFeed;
import com.kmfrog.martlet.feed.domain.TradeLog;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Hello world!
 *
 */
public class App {

    final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcLastAvgTrade;
    final Long2ObjectArrayMap<TrackBook> trackBooks;
    final Map<Long, InstrumentMaker> instrumentMakers;
    final Map<Long, InstrumentTrade> instrumentTrades;
    final DepthFeed depthFeed;
    final TradeFeed tradeFeed;

    public App() {
        multiSrcLastAvgTrade = new ConcurrentHashMap<>();
        trackBooks = new Long2ObjectArrayMap<>();
        instrumentMakers = new ConcurrentHashMap<>();
        instrumentTrades = new ConcurrentHashMap<>();
        depthFeed = new DepthFeed("localhost", 5188, 2);
        tradeFeed = new TradeFeed("localhost", 5288, 2);
    }

    RollingTimeSpan<TradeLog> makesureTradeLog(Source src, long instrument) {
        Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> srcTradeLogs = multiSrcLastAvgTrade.computeIfAbsent(src,
                (key) -> {
                    Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcTradeLogs = new Long2ObjectArrayMap<>();
                    RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS);
                    sameSrcTradeLogs.put(instrument, avgTrade);
                    return sameSrcTradeLogs;
                });
        return srcTradeLogs.computeIfAbsent(instrument, (key) -> {
            RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS);
            return avgTrade;
        });
    }

    TrackBook makesureTrackBook(Instrument instrument) {
        return trackBooks.computeIfAbsent(instrument.asLong(), key -> new TrackBook(instrument));
    }

    InstrumentMaker makesureMaker(Instrument instrument) {
        return instrumentMakers.computeIfAbsent(instrument.asLong(), key -> {
            InstrumentMaker im = new InstrumentMaker(instrument, makesureTrackBook(instrument));
            im.start();
            depthFeed.register(instrument, im);
            return im;
        });

    }

    public static void main(String[] args) {
//        Instrument btcusdt = new Instrument("BTCUSDT", 8, 8);
//        
//        feed.start();
//
//        try {
//            feed.join();
//        } catch (InterruptedException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
    }
}
