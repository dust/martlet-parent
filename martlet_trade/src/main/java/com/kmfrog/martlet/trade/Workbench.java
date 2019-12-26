package com.kmfrog.martlet.trade;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DepthFeed;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.loex.LoexApiRestClient;
import com.kmfrog.martlet.trade.SpringContext;
import com.kmfrog.martlet.trade.bikun.BikunApiRestClient;
import com.kmfrog.martlet.trade.bikun.BikunInstrumentSoloDunk;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.Exec;
import com.kmfrog.martlet.trade.loex.LoexInstrumentSoloDunk;
import com.kmfrog.martlet.trade.tac.TacBalanceSoloDunk;
import com.kmfrog.martlet.trade.tac.TacInstrumentChaser;
import com.kmfrog.martlet.trade.tac.TacInstrumentSoloDunk;
import com.kmfrog.martlet.util.FeedUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.broker.api.client.BrokerApiClientFactory;
import io.broker.api.client.BrokerApiRestClient;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Feed推送数据消费者的工作台。维护系统的关键上下文和状态。
 *
 */
public class Workbench implements Provider {

    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("WorkbenchExecutor-%d").build());
    /** 一定时间窗口的成交流水均值。 **/
    final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcTradeLogs;
    /** 不同来源order book **/
    final Map<Source, Long2ObjectArrayMap<IOrderBook>> multiSrcOrderBooks;
    /** 开放的订单集合 **/
    final Long2ObjectArrayMap<TrackBook> trackBooks;
    /** 交易对的摆盘线程 **/
    final Map<Long, InstrumentMaker> instrumentMakers;
    /** 交易对的实时交易流水处理线程 **/
    final Map<Long, InstrumentTrade> instrumentTrades;
    /** 开放订单跟踪 **/
    OpenOrderTracker openOrderTracker;
    /** 深度数据流 **/
    final DepthFeed depthFeed;
    /** 实时成交数据流 **/
    // final TradeFeed tradeFeed;
    /** 默认来源 **/
    Source[] defSources = C.DEF_SOURCES;
    
    final SpringContext springContext;

    public Workbench(SpringContext springContext) {
    	this.springContext = springContext;
        multiSrcTradeLogs = new ConcurrentHashMap<>();
        multiSrcOrderBooks = new ConcurrentHashMap<>();
        trackBooks = new Long2ObjectArrayMap<>();
        instrumentMakers = new ConcurrentHashMap<>();
        instrumentTrades = new ConcurrentHashMap<>();
        depthFeed = new DepthFeed("localhost", 5188, 2);
        // tradeFeed = new TradeFeed("localhost", 5288, 2);
        // tradeFeed.start();
        depthFeed.start();
    }

    RollingTimeSpan<TradeLog> makesureTradeLog(Source src, long instrument) {
        Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> srcTradeLogs = multiSrcTradeLogs.computeIfAbsent(src, (key) -> {
            Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcTradeLogs = new Long2ObjectArrayMap<>();
            RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(C.LAST_TRADE_WINDOW_MILLIS);
            sameSrcTradeLogs.put(instrument, avgTrade);
            return sameSrcTradeLogs;
        });
        return srcTradeLogs.computeIfAbsent(instrument, (key) -> {
            RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(C.LAST_TRADE_WINDOW_MILLIS);
            return avgTrade;
        });
    }

    /**
     * 获得指定来源及币对的订单簿。Source.Mix 获得相应币对的聚合订单簿。
     * 
     * @param src
     * @param instrument
     * @return
     */
    public IOrderBook makesureOrderBook(Source src, long instrument) {
        Long2ObjectArrayMap<IOrderBook> srcBooks = multiSrcOrderBooks.computeIfAbsent(src,
                (key) -> new Long2ObjectArrayMap<>());
        return srcBooks.computeIfAbsent(instrument,
                (key) -> src == Source.Mix ? new AggregateOrderBook(instrument) : new OrderBook(src, key));
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
        // return instrumentTrades.computeIfAbsent(instrument.asLong(), key -> {
        // InstrumentTrade it = new InstrumentTrade(instrument, defSources,
        // getTradeRollingTimeSpan(defSources, instrument.asLong()));
        // it.start();
        // tradeFeed.register(instrument, it);
        // return it;
        // });
        return null;
    }

    InstrumentMaker makesureMaker(Instrument instrument) {
        return instrumentMakers.computeIfAbsent(instrument.asLong(), key -> {
            InstrumentMaker im = new InstrumentMaker(instrument, makesureTrackBook(instrument), this);
            im.start();
            depthFeed.register(instrument, im);
            return im;
        });
    }

    public RollingTimeSpan<TradeLog> getRollingTradeLog(Source src, Instrument instrument) {
        return makesureTradeLog(src, instrument.asLong());
    }

    public IOrderBook getOrderBook(Source src, Instrument instrument) {
        return makesureOrderBook(src, instrument.asLong());
    }

    public void setOrderBook(Source src, Instrument instrument, IOrderBook book) {
        IOrderBook old = multiSrcOrderBooks.get(src).put(instrument.asLong(), book);
        if (old != null) {
            old.destroy();
            old = null;
        }
    }

    public TrackBook getTrackBook(Source src, Instrument instrument) {
        // 当前没有多来源订单跟踪。
        return makesureTrackBook(instrument);
    }

    public Future<?> submitExec(Exec r) {
        return executor.submit(r);
    }

    void startOpenOrderTracker(Source src, Instrument[] instruments, BrokerApiRestClient client) {
        openOrderTracker = new OpenOrderTracker(src, instruments, client, this);
        openOrderTracker.start();
    }
    
    public void startChaserInstrument(Source src, Instrument instrument, Param param, BrokerApiRestClient client) {
    	if(instrument.asString().equals("HNTCUSDT")) {
	    	TrackBook trackBook = makesureTrackBook(instrument);
	    	InstrumentChaser chaser = new TacInstrumentChaser(instrument, src, trackBook, this, param, client);
	    	chaser.start();
	    	depthFeed.registerChaser(instrument, chaser);
    	}
    }

    public void startHedgeInstrument(Source src, Instrument instrument, Param param, BrokerApiRestClient client) {
        TrackBook trackBook = makesureTrackBook(instrument);
        makesureTradeLog(src, instrument.asLong());
        
//        TacBalanceSoloDunk solodunk = new TacBalanceSoloDunk(instrument, src, trackBook, this, param, client);
        TacInstrumentSoloDunk solodunk = new TacInstrumentSoloDunk(instrument, src, trackBook, this, client, param);
        solodunk.start();
        depthFeed.register(instrument, solodunk);
    }
    
    public void startLoexHedgeInstrument(Source src, Instrument instrument, Param param, LoexApiRestClient client) {
    	TrackBook trackBook = makesureTrackBook(instrument);
    	makesureTradeLog(src, instrument.asLong());
    	LoexInstrumentSoloDunk solodunk = new LoexInstrumentSoloDunk(instrument, src, trackBook, this, param, client);
    	solodunk.start();
    	depthFeed.register(instrument,  solodunk);
    }
    
    public void startBikunHedgeInstrument(Source src, Instrument instrument, Param param, BikunApiRestClient client) {
    	TrackBook trackBook = makesureTrackBook(instrument);
    	makesureTradeLog(src, instrument.asLong());
     	BikunInstrumentSoloDunk solodunk =  new BikunInstrumentSoloDunk(instrument, src, trackBook, this, client, param);
     	solodunk.start();
     	depthFeed.register(instrument, solodunk);
    }

    public void startOccupyInstrument(Source src, Instrument ca, Instrument ab, Instrument cb,
            BrokerApiRestClient client, Param caArgs, Param cbArgs) {
        TrackBook caTracker = makesureTrackBook(ca);
        TrackBook cbTracker = makesureTrackBook(cb);

        makesureMaker(ab);
        makesureMaker(cb);
        makesureOrderBook(src, ca.asLong());
        makesureOrderBook(src, ab.asLong());
        makesureOrderBook(src, cb.asLong());
        makesureTradeLog(src, ca.asLong());
        makesureTradeLog(src, cb.asLong());

        TriangleOccupyInstrument caOccupy = new TriangleOccupyInstrument(ca, ab, cb, false, src, caTracker, this,
                client, caArgs);
//        TriangleOccupyInstrument cbOccupy = new TriangleOccupyInstrument(cb, ab, ca, true, src, cbTracker, this, client,
//                cbArgs);

        caOccupy.start();
//        cbOccupy.start();
//        depthFeed.register(cb, cbOccupy);
        depthFeed.register(ca, caOccupy);
    }

    public void start(Source src, String[] instrumentNames, Map<String, Instrument> instruments,
            Map<String, Param> cfgArgs, BrokerApiRestClient client) {
        for (String instrumentName : instrumentNames) {
            startHedgeInstrument(src, instruments.get(instrumentName), cfgArgs.get(instrumentName), client);
//            startChaserInstrument(src, instruments.get(instrumentName), cfgArgs.get(instrumentName), client);
        }
    }
    
    public void startBikun(Source src, String[] instrumentNames, Map<String, Instrument> instruments,
    	Map<String, Param> cfgArgs, BikunApiRestClient client) {
    	for(String instrumentName : instrumentNames) {
    		startBikunHedgeInstrument(src, instruments.get(instrumentName), cfgArgs.get(instrumentName), client);
    	}
    }
    
    public void startLoex(Source src, String[] instrumentNames, Map<String, Instrument> instruments,
    	Map<String, Param> cfgArgs, LoexApiRestClient client) {
    	for(String instrumentName : instrumentNames) {
    		startLoexHedgeInstrument(src, instruments.get(instrumentName), cfgArgs.get(instrumentName), client);
    	}
    }
    
    public int getSpreadLowLimitMillesimal() {
        return this.springContext.getSpreadLowLimitMillesimal();
    }
    
    public int getMaxLevel() {
    	return springContext.getMaxLevel();
    }


    public static void main(String[] args) {
//        Workbench app = new Workbench();
//        Config cfg = ConfigFactory.load();
//        String baseUrl = cfg.getString("api.base.url");
//        String apiKey = cfg.getString("api.key");
//        String secret = cfg.getString("api.secret");
//
//        BrokerApiRestClient client = BrokerApiClientFactory.newInstance(baseUrl, apiKey, secret).newRestClient();
//        Map<String, Object> cfgArgs = FeedUtils.parseConfigArgs(cfg.getString("hedge.args"));
//        List<Instrument> hedgeInstruments = FeedUtils.parseInstruments(cfg.getString("instruments"));
//        List<Instrument> occupyInstruments = FeedUtils.parseInstruments(cfg.getString("triangle.instruments"));
//        List<Instrument> all = new ArrayList<>();
//        all.addAll(hedgeInstruments);
//        all.addAll(occupyInstruments);
//        // app.start(Source.Bhex, hedgeInstruments, all, cfgArgs, client);
//        Instrument ca = occupyInstruments.get(0);
//        Instrument ab = occupyInstruments.get(1);
//        Instrument cb = occupyInstruments.get(2);
//        Map<String, String> caArgs = (Map<String, String>) cfgArgs.get(ca.asString());
//        Map<String, String> cbArgs = (Map<String, String>) cfgArgs.get(cb.asString());
//
//        app.startOpenOrderTracker(Source.Bhex, all.toArray(new Instrument[all.size()]), client);
//
//        // app.startOccupyInstrument(Source.Bhex, ca, ab, cb, client, caArgs, cbArgs);
//        // app.startHedgeInstrument(Source.Bhex, ca, caArgs, client);
//        // app.startHedgeInstrument(Source.Bhex, cb, cbArgs, client);
//
//        try {
//            while (true) {
//                Thread.sleep(10000);
//            }
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        try {
//            // app.tradeFeed.quit();
//            app.depthFeed.quit();
//            Thread.sleep(100);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }

    }
}
