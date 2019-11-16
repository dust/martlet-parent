package com.kmfrog.martlet.trade;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DepthFeed;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.TradeFeed;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.exec.Exec;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Feed推送数据消费者的工作台。维护系统的关键上下文和状态。
 *
 */
public class Workbench implements Provider {

    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("WorkbenchExecutor-%d").build());
    /** 一定时间窗口的实时成交流水均值。 **/
    final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcLastAvgTrade;
    /** 开放的订单集合 **/
    final Long2ObjectArrayMap<TrackBook> trackBooks;
    /** 交易对的摆盘线程 **/
    final Map<Long, InstrumentMaker> instrumentMakers;
    /** 交易对的实时交易流水处理线程 **/
    final Map<Long, InstrumentTrade> instrumentTrades;
    /** 深度数据流 **/
    final DepthFeed depthFeed;
    /** 实时成交数据流 **/
    final TradeFeed tradeFeed;
    /** 默认来源 **/
    Source[] defSources = C.DEF_SOURCES;

    public Workbench() {
        multiSrcLastAvgTrade = new ConcurrentHashMap<>();
        trackBooks = new Long2ObjectArrayMap<>();
        instrumentMakers = new ConcurrentHashMap<>();
        instrumentTrades = new ConcurrentHashMap<>();
        depthFeed = new DepthFeed("localhost", 5188, 2);
        tradeFeed = new TradeFeed("localhost", 5288, 2);
        tradeFeed.start();
        depthFeed.start();
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

    RollingTimeSpan<TradeLog>[] getTradeRollingTimeSpan(Source[] sources, long instrument) {
        @SuppressWarnings("unchecked")
        RollingTimeSpan<TradeLog>[] rollings = new RollingTimeSpan[sources.length];
        for (int i = 0; i < sources.length; i++) {
            rollings[i] = makesureTradeLog(sources[i], instrument);
        }
        return rollings;
    }

    InstrumentTrade makesureTrade(Instrument instrument) {
        return instrumentTrades.computeIfAbsent(instrument.asLong(), key -> {
            InstrumentTrade it = new InstrumentTrade(instrument, defSources,
                    getTradeRollingTimeSpan(defSources, instrument.asLong()));
            it.start();
            tradeFeed.register(instrument, it);
            return it;
        });
    }

    InstrumentMaker makesureMaker(Instrument instrument) {
        return instrumentMakers.computeIfAbsent(instrument.asLong(), key -> {
            InstrumentMaker im = new InstrumentMaker(instrument, makesureTrackBook(instrument), this);
            im.start();
            depthFeed.register(instrument, im);
            return im;
        });
    }

    public RollingTimeSpan<TradeLog> getAvgTrade(Source src, Instrument instrument) {
        return makesureTradeLog(src, instrument.asLong());
    }

    public IOrderBook getOrderBook(Source src, Instrument instrument) {
        InstrumentMaker im = makesureMaker(instrument);
        return im.getOrderBook(src);
    }
    
    public Future<?> submitExec(Exec r) {
        return executor.submit(r);
    }

    public static void main(String[] args) {
        Workbench app = new Workbench();
        Instrument instrument = new Instrument("BTCUSDT", 8, 8);
        RollingTimeSpan<TradeLog> huobiAvgTrade = app.makesureTradeLog(Source.Huobi, instrument.asLong());
        RollingTimeSpan<TradeLog> binanceAvgTrade = app.makesureTradeLog(Source.Binance, instrument.asLong());
        RollingTimeSpan<TradeLog> okexAvgTrade = app.makesureTradeLog(Source.Okex, instrument.asLong());
        InstrumentTrade btcTrade = app.makesureTrade(instrument);

        TrackBook btcOpenOrders = app.makesureTrackBook(instrument);
        InstrumentMaker btcMaker = app.makesureMaker(instrument);

        try {
            btcMaker.join();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            app.tradeFeed.quit();
            app.depthFeed.quit();
            btcMaker.quit();
            btcTrade.quit();

            Thread.sleep(100);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }
}
