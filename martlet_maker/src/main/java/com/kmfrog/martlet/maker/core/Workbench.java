package com.kmfrog.martlet.maker.core;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.domain.VolumeStrategy;
import com.kmfrog.martlet.maker.SpringContext;
import com.kmfrog.martlet.maker.exec.Exec;
import com.kmfrog.martlet.maker.feed.DepthFeed;
import com.kmfrog.martlet.maker.feed.TradeFeed;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Hello world!
 *
 */
public class Workbench implements Provider {

    private final Logger logger = LoggerFactory.getLogger(Workbench.class);

    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("Maker-Exec-%d").build());

    /** 一定时间窗口的实时成交流水均值。 **/
    final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcLastAvgTrade;

    /** 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿 **/
    private final Map<Source, Long2ObjectArrayMap<IOrderBook>> multiSrcBooks;
    /** 币对的聚合订单簿 **/
    private final Long2ObjectArrayMap<AggregateOrderBook> aggBooks;
    /** 开放的订单集合 **/
    final Long2ObjectArrayMap<TrackBook> trackBooks;
    /** 开放的订单跟踪者 **/
    OpenOrderTracker openOrderTracker;
    /** 交易对的摆盘线程 **/
    final Map<Long, InstrumentMaker> instrumentMakers;
    /** 交易对的实时交易流水处理线程 **/
    final Map<Long, InstrumentTrade> instrumentTrades;
    /** 深度数据流 **/
    final DepthFeed depthFeed;
    /** 实时成交数据流 **/
    final TradeFeed tradeFeed;

    final TradeService tradeService;

    final DepthService depthService;

    final SpringContext springContext;

    public Workbench(DepthService depthService, TradeService tradeService, SpringContext springContext) {
        this.tradeService = tradeService;
        this.depthService = depthService;
        this.springContext = springContext;
        multiSrcLastAvgTrade = new ConcurrentHashMap<>();
        multiSrcBooks = new ConcurrentHashMap<>();
        trackBooks = new Long2ObjectArrayMap<>();
        aggBooks = new Long2ObjectArrayMap<>();
        instrumentMakers = new ConcurrentHashMap<>();
        instrumentTrades = new ConcurrentHashMap<>();

        String depthHost = springContext.getDepthFeedHost();
        int depthPort = springContext.getDepthFeedPort();
        int depthThreads = springContext.getDepthFeedThreadCnt();
        depthFeed = new DepthFeed(depthHost, depthPort, depthThreads);
        depthFeed.start();

        String tradeHost = springContext.getTradeFeedHost();
        int tradePort = springContext.getTradeFeedPort();
        int tradeThreads = springContext.getTradeFeedThreadCnt();
        tradeFeed = new TradeFeed(tradeHost, tradePort, tradeThreads);
        tradeFeed.start();

        openOrderTracker = new OpenOrderTracker(Source.Tatmas, null, this);
        openOrderTracker.start();
    }

    RollingTimeSpan<TradeLog> makesureTradeLog(Source src, long instrument) {
        final long avgWindowMillis = springContext.getTradeAvgWindowMillis();
        Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> srcTradeLogs = multiSrcLastAvgTrade.computeIfAbsent(src,
                (key) -> {
                    Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcTradeLogs = new Long2ObjectArrayMap<>();
                    RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(avgWindowMillis);
                    sameSrcTradeLogs.put(instrument, avgTrade);
                    return sameSrcTradeLogs;
                });
        return srcTradeLogs.computeIfAbsent(instrument, (key) -> {
            RollingTimeSpan<TradeLog> avgTrade = new RollingTimeSpan<TradeLog>(avgWindowMillis);
            return avgTrade;
        });
    }

    IOrderBook makesureOrderBook(Source src, long instrument) {
        Long2ObjectArrayMap<IOrderBook> srcBooks = multiSrcBooks.computeIfAbsent(src,
                (key) -> new Long2ObjectArrayMap<>());
        return srcBooks.computeIfAbsent(instrument, (key) -> new OrderBook(src, instrument));
    }

    public void setOrderBook(Source src, Instrument instrument, IOrderBook book) {
        IOrderBook old = null;
        if (src == Source.Mix) {
            old = aggBooks.put(instrument.asLong(), (AggregateOrderBook) book);
        } else {
            Long2ObjectArrayMap<IOrderBook> sameSrcBooks = multiSrcBooks.get(src);
            if (sameSrcBooks != null) {
                old = sameSrcBooks.put(instrument.asLong(), book);
            }
        }

        // if (old != null) {
        // old.destroy();
        // old = null;
        // }

    }

    @Override
    public DepthService getDepthService() {
        return depthService;
    }

    @Override
    public TradeService getTradeService() {
        return tradeService;
    }

    public Source getPreferSource(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_PREFER_SOURCE) && node.get(CFG_PREFER_SOURCE).asText().length() > 0) {
        // return Source.valueOf(node.get(CFG_PREFER_SOURCE).asText());
        // }
        // }
        return Source.valueOf(springContext.getPreferSourceName());
    }

    public int getSpreadLowLimitMillesimal(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_SPREAD_LOWLIMIT_MILLESIMAL)
        // && node.get(CFG_SPREAD_LOWLIMIT_MILLESIMAL).asText().length() > 0) {
        // return node.get(CFG_SPREAD_LOWLIMIT_MILLESIMAL).asInt();
        // }
        // }
        return springContext.getSpreadLowLimitMillesimal();
    }

    public Source[] getAllSource() {
        return springContext.getAllSource();
    }

    public double getMaxPriceDiff(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_PRICE_DIFF) && node.get(CFG_PRICE_DIFF).asText().length() > 0) {
        // return node.get(CFG_PRICE_DIFF).asDouble();
        // }
        // }
        return springContext.getMaxPriceDiff();
    }

    public long getMaxDelayMillis(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_MAX_DELAY_MILLIS) && node.get(CFG_MAX_DELAY_MILLIS).asText().length() > 0) {
        // return node.get(CFG_MAX_DELAY_MILLIS).asLong();
        // }
        // }
        return springContext.getMaxDelayMillis();
    }

    @Override
    public double getMaxVolumeDiff(Instrument instrument) {
        // SymbolAoWithFeatureAndExtra symbolInfo = getSymbolInfo(instrument);
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_MAX_MAKE_ORDER_VOL_DIFF) && node.get(CFG_MAX_MAKE_ORDER_VOL_DIFF).asText().length() > 0) {
        // return node.get(CFG_MAX_MAKE_ORDER_VOL_DIFF).asDouble();
        // }
        // }
        return springContext.getMaxMakeOrderVolDiff();
    }

    @Override
    public IOrderBook getOrderBook(Source src, Instrument instrument) {
        return makesureOrderBook(src, instrument.asLong());
    }

    public AggregateOrderBook getAggBook(Instrument instrument) {
        return makesureAggregateOrderBook(instrument.asLong());
    }

    private AggregateOrderBook makesureAggregateOrderBook(long asLong) {
        return aggBooks.computeIfAbsent(asLong, key -> new AggregateOrderBook(asLong));
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
            InstrumentTrade it = new InstrumentTrade(instrument, this);
            getTradeRollingTimeSpan(springContext.getAllSource(), instrument.asLong());
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

    @Override
    public TrackBook getTrackBook(Source src, Instrument instrument) {
        return makesureTrackBook(instrument);
    }

    @Override
    public SymbolAoWithFeatureAndExtra getSymbolInfo(Instrument instrument) {
        // return cccClient.getSymbolInfoByName(instrument.asString().toLowerCase());
        return null;
    }
    
    public BigDecimal getTradeVolumeFactor(SymbolAoWithFeatureAndExtra symbolInfo) {
        return springContext.getTradeVolumeFactor();
    }

    @Override
    public Set<String> getSplitTradeSymbols() {
        return springContext.getSplitTradeSymbols();
    }

    @Override
    public long getSplitTradeMaxDelayMillis(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        //
        // if (node.has(CFG_SPLIT_TRADE_MAX_DELAY_MILLIS)
        // && node.get(CFG_SPLIT_TRADE_MAX_DELAY_MILLIS).asText().length() > 0) {
        // return node.get(CFG_SPLIT_TRADE_MAX_DELAY_MILLIS).asLong();
        // }
        // }
        return springContext.getSplitTradeMaxDelayMillis();
    }

    @Override
    public double getSplitTradeRatio(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_SPLIT_TRADE_RATIO) && node.get(CFG_SPLIT_TRADE_RATIO).asText().length() > 0) {
        // return node.get(CFG_SPLIT_TRADE_RATIO).asDouble();
        // }
        // }
        return springContext.getSplitTradeRatio();
    }

    @Override
    public long getMakerSleepMillis(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_MAKER_SLEEP_MILLIS) && node.get(CFG_MAKER_SLEEP_MILLIS).asText().length() > 0) {
        // return node.get(CFG_MAKER_SLEEP_MILLIS).asLong();
        // }
        // }
        return springContext.getMakerSleepMillis();
    }

    @Override
    public int getMaxLevel(SymbolAoWithFeatureAndExtra symbolInfo) {
        // if (symbolInfo != null && symbolInfo.getExtra() != null && symbolInfo.getExtra().has(LOKI_CONFIG)) {
        // JsonNode node = symbolInfo.getExtra().get(LOKI_CONFIG);
        // if (node.has(CFG_MAX_LEVEL) && node.get(CFG_MAX_LEVEL).asText().length() > 0) {
        // return node.get(CFG_MAX_LEVEL).asInt();
        // }
        // }
        return springContext.getMaxLevel();
    }

    @Override
    public long getOpenOrderSleepMillis() {
        return springContext.getOpenOrderTrackerSleepMillis();
    }

    @Override
    public List<VolumeStrategy> getVolumeStrategy(SymbolAoWithFeatureAndExtra symbolInfo) {
        // try {
        // if (symbolInfo != null && symbolInfo.getExtra() != null
        // && symbolInfo.getExtra().has(CFG_VOLUME_STRATEGIES_KEY)) {
        // JsonNode node = symbolInfo.getExtra().get(CFG_VOLUME_STRATEGIES_KEY);
        // return JsonUtil.decode(node, new ParameterizedTypeReference<List<VolumeStrategy>>() {
        // });
        // }
        // } catch (Exception ex) {
        // logger.warn("{} getVolumeStrategy {}", symbolInfo.getSymbolName(), ex.getMessage());
        // }
        return springContext.getVolumeStrategies();
    }
    
    

    @Override
    public int getBuyRobotId(SymbolAoWithFeatureAndExtra symbolInfo) {
        return springContext.getBuyRobotId();
    }

    @Override
    public int getSellRobotId(SymbolAoWithFeatureAndExtra symbolInfo) {
        return springContext.getSellRobotId();
    }

    @Override
    public int getMakerTradeUserId(SymbolAoWithFeatureAndExtra symbolInfo) {
        return springContext.getMakerTradeUserId();
    }

    @Override
    public Future<?> submit(Exec exec) {
        return executor.submit(exec);
    }
    
    @Override
    public String getTatmasApiKey() {
    	return springContext.getTatmasApiKey();
    }
    
    @Override
    public String getTatmasSecretKey() {
    	return springContext.getTatmasSecretKey();
    }
    
    @Override
    public String getTatmasTradeApiKey() {
    	return springContext.getTatmasTradeApiKey();
    }
    
    @Override
    public String getTatmasTradeSecretKey() {
    	return springContext.getTatmasTradeSecretKey();
    }

    public void destory() {
        depthFeed.quit();
        tradeFeed.quit();
        openOrderTracker.quit();
        instrumentMakers.forEach((k, v) -> v.quit());
        instrumentTrades.forEach((k, v) -> v.quit());
        executor.shutdown();
    }

    public void start(Map<String, Instrument> all) {
        Collection<Instrument> instruments = all.values();
        Set<Long> allNew = instruments.stream().map(Instrument::asLong).collect(Collectors.toSet());

        openOrderTracker.setup(instruments);

        Set<Long> makers = instrumentMakers.keySet();
        Set<Long> traders = instrumentTrades.keySet();
        final Set<Long> allExists = Sets.union(makers, traders);

        Set<Long> addSet = Sets.difference(allNew, allExists);
        Set<Long> rmSet = Sets.difference(allExists, allNew);

        if (logger.isInfoEnabled()) {
            // Map<Long, Instrument> lngInstruments = instruments.stream().collect(Collectors.toMap(Instrument::asLong,
            // v->v));
            // logger.info("start: {}, stop:{} ", addSet.stream().collect(Collectors.toMap(v->v,
            // v->lngInstruments.get(v))), rmSet.stream().collect(Collectors.toMap(v->v, v->lngInstruments.get(v))));
            logger.info(" loki all:{}|{} start: {} stop: {}", allNew, all, addSet, rmSet);
        }

        Source[] defSources = springContext.getAllSource();
        if (addSet.size() > 0) {
            for (Instrument inst : instruments) {
                if (addSet.contains(inst.asLong())) {
                    for (Source src : defSources) {
                        makesureTradeLog(src, inst.asLong());
                        makesureOrderBook(src, inst.asLong());
                    }
//                    makesureAggregateOrderBook(inst.asLong());
                    makesureMaker(inst);
                    makesureTrade(inst);
                }
            }
        }

        if (rmSet.size() > 0) {
            for (Long lng : rmSet) {
                for (Source src : defSources) {
                    Long2ObjectArrayMap<IOrderBook> sameSrcBooks = multiSrcBooks.get(src);
                    if (sameSrcBooks != null) {
                        sameSrcBooks.remove(lng.longValue());
                    }

                    Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcRollings = multiSrcLastAvgTrade.get(src);
                    if (sameSrcRollings != null) {
                        sameSrcRollings.remove(lng.longValue());
                    }
                }

//                aggBooks.remove(lng.longValue());
                InstrumentMaker maker = instrumentMakers.get(lng);
                if (maker != null) {
                    depthFeed.unregister(lng);
                    maker.quit();
                }
                InstrumentTrade trade = instrumentTrades.get(lng);
                if (trade != null) {
                    tradeFeed.unregister(lng);
                    trade.quit();
                }

            }
        }

    }
}
