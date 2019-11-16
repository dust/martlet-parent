package com.kmfrog.martlet.feed;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.impl.BinanceDepthHandler;
import com.kmfrog.martlet.feed.impl.BinanceInstrumentDepth;
import com.kmfrog.martlet.feed.impl.BinanceInstrumentTrade;
import com.kmfrog.martlet.feed.impl.BinanceTradeHandler;
import com.kmfrog.martlet.feed.impl.HuobiDepthHandler;
import com.kmfrog.martlet.feed.impl.HuobiInstrumentDepth;
import com.kmfrog.martlet.feed.impl.HuobiInstrumentTrade;
import com.kmfrog.martlet.feed.impl.HuobiTradeHandler;
import com.kmfrog.martlet.feed.net.FeedBroadcast;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * 工作台。
 * 
 * @author dust Nov 8, 2019
 *
 */
public class Workbench implements Controller {

    private static final Logger logger = LoggerFactory.getLogger(Workbench.class);

    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("MartletAppExecutor-%d").build());

    /**
     * 所有交易对的聚合订单表。
     **/
    private final Long2ObjectArrayMap<AggregateOrderBook> aggBooks;

    /**
     * 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿。
     **/
    private final Map<Source, Long2ObjectArrayMap<IOrderBook>> multiSrcBooks;

//    private final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcAvgTrades;

    /**
     * 聚合工作线程。{instrument.asLong : worker}
     **/
    private final Map<Long, InstrumentAggregation> aggWorkers;

    /**
     * trade流，推送线程
     **/
    private Pusher tradePusher;

    /**
     * 深度推送线程
     */
    private final Pusher depthPusher;

    /**
     * 深度websocket集合。来源为key.
     **/
    private final Map<Source, WebSocketDaemon> depthWsDaemons;

    /**
     * trade流websocket集合。`key`值为来源
     **/
    private final Map<Source, WebSocketDaemon> tradeWsDaemons;

    /**
     * 深度ZMQ广播
     **/
    private final FeedBroadcast depthFeed;

    /**
     * 实时交易广播
     **/
    private final FeedBroadcast tradeFeed;

    /** 状态监控线程 **/
    private final Monitor monitor;
    private final Map<Object, Object> cfg;

    public Workbench(Map<Object, Object> cfg) {
        this.cfg = cfg;

        String depthHost = getString(cfg, C.PUB_DEPTH_HOST);
        int depthPort = getInt(cfg, C.PUB_DEPTH_PORT, 5188);
        int depthIoThreads = getInt(cfg, C.PUB_DEPTH_IO_THREAD_CNT, 2);
        depthFeed = new FeedBroadcast(depthHost, depthPort, depthIoThreads);

        String tradeHost = getString(cfg, C.PUB_TRADE_HOST);
        int tradePort = getInt(cfg, C.PUB_TRADE_PORT, 5288);
        int tradeIoThreads = getInt(cfg, C.PUB_TRADE_IO_THREAD_CNT, 2);
        tradeFeed = new FeedBroadcast(tradeHost, tradePort, tradeIoThreads);

        depthPusher = new Pusher(depthFeed, this);
        tradePusher = new Pusher(tradeFeed, this);
        monitor = new Monitor();

        depthPusher.start();
        tradePusher.start();
        monitor.start();

        multiSrcBooks = new ConcurrentHashMap<>();
//        multiSrcAvgTrades = new ConcurrentHashMap<>();
        aggBooks = new Long2ObjectArrayMap<>();
        aggWorkers = new ConcurrentHashMap<>();

        tradeWsDaemons = new ConcurrentHashMap<>();
        depthWsDaemons = new ConcurrentHashMap<>();
    }

    /**
     * 获得某个来源，指定`instrument`的订单簿。
     *
     * @param src
     * @param instrument
     * @return
     */
    IOrderBook makesureOrderBook(Source src, long instrument) {
        Long2ObjectArrayMap<IOrderBook> srcBooks = multiSrcBooks.computeIfAbsent(src, (key) -> {
            Long2ObjectArrayMap<IOrderBook> sameSrcBooks = new Long2ObjectArrayMap<>();
            sameSrcBooks.put(instrument, new OrderBook(src, instrument));
            return sameSrcBooks;
        });
        return srcBooks.computeIfAbsent(instrument, (key) -> new OrderBook(src, key));
    }

//    RollingTimeSpan<TradeLog> makesureRollingAvgTrade(Source src, long instrument) {
//        Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> srcAvgTrades = multiSrcAvgTrades.computeIfAbsent(src, (key) -> {
//            Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcAvgTrades = new Long2ObjectArrayMap<>();
//            sameSrcAvgTrades.put(instrument, new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS));
//            return sameSrcAvgTrades;
//        });
//        return srcAvgTrades.computeIfAbsent(instrument,
//                (key) -> new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS));
//    }

    /**
     * 获得指·`instrument`的聚合订单簿。并确认聚合工作线程开始工作。
     *
     * @param instrument
     * @return
     */
    AggregateOrderBook makesureAggregateOrderBook(Instrument instrument) {
        AggregateOrderBook book = aggBooks.computeIfAbsent(instrument.asLong(), (key) -> new AggregateOrderBook(key));
        if (!aggWorkers.containsKey(instrument.asLong())) {
            InstrumentAggregation worker = new InstrumentAggregation(instrument, book, depthPusher, this, C.MAX_LEVEL);
            aggWorkers.put(instrument.asLong(), worker);
            worker.start();
        }
        return book;
    }

    void startSnapshotTask(String url, SnapshotDataListener listener) {
        Runnable r = new RestSnapshotRunnable(url, "GET", null, null, listener);
        executor.submit(r);
    }

    static void startWebSocket(Map<Source, WebSocketDaemon> daemons, Source source, BaseWebSocketHandler handler) {
        WebSocketDaemon wsDaemon = new WebSocketDaemon(handler);
        daemons.put(source, wsDaemon);
        wsDaemon.keepAlive();
    }

    private void printSymbol(Instrument instrument) {
        AggregateOrderBook aggBook = makesureAggregateOrderBook(instrument);
        IOrderBook binanceBook = makesureOrderBook(Source.Binance, instrument.asLong());
        IOrderBook hbBook = makesureOrderBook(Source.Huobi, instrument.asLong());
        IOrderBook okexBook = makesureOrderBook(Source.Okex, instrument.asLong());

        System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", binanceBook.getBestBidPrice(),
                binanceBook.getBestAskPrice(), hbBook.getBestBidPrice(), hbBook.getBestAskPrice(),
                okexBook.getBestBidPrice(), okexBook.getBestAskPrice(), aggBook.getBestBidPrice(),
                aggBook.getBestAskPrice());
        System.out.format("\n\n%s\n", aggBook.getOriginText(Source.Mix, 5));
        
//        RollingTimeSpan<TradeLog> hbAvg = makesureRollingAvgTrade(Source.Huobi, instrument.asLong());
//        System.out.format("%d|%d|%d", hbAvg.avg(), hbAvg.first(), hbAvg.last());
        System.out.println("\n\n");
    }

    @Override
    public void reset(Source mkt, Instrument instrument, BaseInstrumentDepth depth, boolean isSubscribe,
            boolean isConnect) {
        // this.startSnapshotTask(instrument.asString().toUpperCase(), depth);
        depthWsDaemons.get(mkt).reset(instrument, depth, isSubscribe, isConnect);
    }

    @Override
    public void resetBook(Source mkt, Instrument instrument, IOrderBook book) {
        try {
            if (aggWorkers.containsKey(instrument.asLong())) {
                aggWorkers.get(instrument.asLong()).putMsg(mkt, book);
            }
            if (book != null) {
                depthPusher.put(book.getOriginText(mkt, C.MAX_LEVEL));
            }
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void logTrade(Source src, Instrument instrument, long id, long price, long volume, long cnt, boolean isBuy,
            long ts, long recvTs) {
        TradeLog log = new TradeLog(src, instrument.asLong(), id, price, volume, cnt, isBuy, ts, recvTs);
        tradePusher.put(StringUtils.join(log.toLongArray(), C.SEPARATOR));
//        RollingTimeSpan<TradeLog> avgTrade = makesureRollingAvgTrade(src, instrument.asLong());
//        avgTrade.add(log);

    }

    @Override
    public void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk,
            long lastUpdate, long lastReceived) {
    }

    public void start(List<Instrument> supportedInstruments, Set<String> binanceSymbols, Set<String> huobiSymbols,
            Set<String> okexSymbols) {
        // Set<String> all =
        // Arrays.asList(supportedInstruments).stream().map(instrument->instrument.asString()).collect(Collectors.toSet());
        // Set<String> bnSymbols = Sets.intersection(all, binanceSymbols);
        // Set<String> hbSymbols = Sets.intersection(all, huobiSymbols);
        // Set<String> okSymbols = Sets.intersection(all, okexSymbols);
        Map<String, Instrument> all = supportedInstruments.stream()
                .collect(Collectors.toMap(Instrument::asString, instrument -> instrument));
        Set<String> bnExistSymbols = depthWsDaemons.get(Source.Binance) == null ? new HashSet<>()
                : depthWsDaemons.get(Source.Binance).getSymbolNames();
        Set<String> hbExistSymbols = depthWsDaemons.get(Source.Huobi) == null ? new HashSet<>()
                : depthWsDaemons.get(Source.Huobi).getSymbolNames();
        Set<String> okExistSymbols = depthWsDaemons.get(Source.Okex) == null ? new HashSet<>()
                : depthWsDaemons.get(Source.Okex).getSymbolNames();
        Set<String> bnAddSymbols = Sets.difference(binanceSymbols, bnExistSymbols);
        Set<String> bnRmSymbols = Sets.difference(bnExistSymbols, binanceSymbols);
        Set<String> hbAddSymbols = Sets.difference(huobiSymbols, hbExistSymbols);
        Set<String> okAddSymbols = Sets.difference(okexSymbols, okExistSymbols);
        Set<String> hbRmSymbols = Sets.difference(hbExistSymbols, huobiSymbols);
        Set<String> okRmSymbols = Sets.difference(okExistSymbols, okexSymbols);

        setupBinance(all, binanceSymbols, bnAddSymbols, bnRmSymbols);
        setupHuobi(all, hbAddSymbols, hbRmSymbols);
        setupOkex(all, okAddSymbols, okRmSymbols);
    }

    private void setupOkex(Map<String, Instrument> instruments, Set<String> okAddSymbols, Set<String> okRmSymbols) {
        int length = okAddSymbols.size();
        if (length == 0 && okRmSymbols.size() == 0) {
            return;
        }

        Source okex = Source.Huobi;
        if (depthWsDaemons.containsKey(okex)) {
            // 增量
            WebSocketDaemon ws = depthWsDaemons.get(okex);
            for (String symbol : okAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                IOrderBook book = makesureOrderBook(okex, instrument.asLong());
                ws.subscribeSymbol(symbol, new HuobiInstrumentDepth(instrument, book, this));
            }

            for (String symbol : okRmSymbols) {
                ws.unsubscribeSymbols(symbol);
            }
        }
        if (tradeWsDaemons.containsKey(okex)) {
            // 增量
            WebSocketDaemon ws = tradeWsDaemons.get(okex);
            for (String symbol : okAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                ws.subscribeSymbol(symbol, new HuobiInstrumentTrade(instrument, this));
            }

            for (String symbol : okRmSymbols) {
                ws.unsubscribeSymbols(symbol);
            }
        }

        if (!depthWsDaemons.containsKey(okex)) {
            // 首次连接websocket
            WsDataListener[] depthListeners = new WsDataListener[length];
            String[] symbolArray = okAddSymbols.toArray(new String[length]);
            Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(okex);

            int i = 0;
            for (String symbol : okAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                IOrderBook book = makesureOrderBook(okex, instrument.asLong());
                depthListeners[i] = new HuobiInstrumentDepth(instrument, book, this);
                i++;
            }

            String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
            String depthFmt = getString(hbMap, C.HUOBI_DEPTH_FMT);
            HuobiDepthHandler handler = new HuobiDepthHandler(wsUrl, depthFmt, symbolArray, depthListeners);
            startWebSocket(depthWsDaemons, okex, handler);
        }

        if (!tradeWsDaemons.containsKey(okex)) {
            // 首次连接websocket
            WsDataListener[] tradeListeners = new WsDataListener[length];
            String[] symbolArray = okAddSymbols.toArray(new String[length]);
            Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(okex);

            int i = 0;
            for (String symbol : okAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                tradeListeners[i] = new HuobiInstrumentTrade(instrument, this);
                i++;
            }

            String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
            String tradeFmt = getString(hbMap, C.HUOBI_TRADE_FMT);
            HuobiTradeHandler tradeHandler = new HuobiTradeHandler(wsUrl, tradeFmt, symbolArray, tradeListeners);
            startWebSocket(tradeWsDaemons, okex, tradeHandler);
        }
    }

    private void setupHuobi(Map<String, Instrument> instruments, Set<String> hbAddSymbols, Set<String> hbRmSymbols) {
        int length = hbAddSymbols.size();
        if (length == 0 && hbRmSymbols.size() == 0) {
            return;
        }

        if (depthWsDaemons.containsKey(Source.Huobi)) {
            // 增量
            WebSocketDaemon ws = depthWsDaemons.get(Source.Huobi);
            for (String symbol : hbAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                IOrderBook book = makesureOrderBook(Source.Huobi, instrument.asLong());
                ws.subscribeSymbol(symbol, new HuobiInstrumentDepth(instrument, book, this));
            }

            for (String symbol : hbRmSymbols) {
                ws.unsubscribeSymbols(symbol);
            }
        }
        if (tradeWsDaemons.containsKey(Source.Huobi)) {
            // 增量
            WebSocketDaemon ws = tradeWsDaemons.get(Source.Huobi);
            for (String symbol : hbAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                ws.subscribeSymbol(symbol, new HuobiInstrumentTrade(instrument, this));
            }

            for (String symbol : hbRmSymbols) {
                ws.unsubscribeSymbols(symbol);
            }
        }

        if (!depthWsDaemons.containsKey(Source.Huobi)) {
            // 首次连接websocket
            WsDataListener[] depthListeners = new WsDataListener[length];
            String[] symbolArray = hbAddSymbols.toArray(new String[length]);
            Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(Source.Huobi);

            int i = 0;
            for (String symbol : hbAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                IOrderBook book = makesureOrderBook(Source.Huobi, instrument.asLong());
                depthListeners[i] = new HuobiInstrumentDepth(instrument, book, this);
                i++;
            }

            String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
            String depthFmt = getString(hbMap, C.HUOBI_DEPTH_FMT);
            HuobiDepthHandler handler = new HuobiDepthHandler(wsUrl, depthFmt, symbolArray, depthListeners);
            startWebSocket(depthWsDaemons, Source.Huobi, handler);
        }

        if (!tradeWsDaemons.containsKey(Source.Huobi)) {
            // 首次连接websocket
            WsDataListener[] tradeListeners = new WsDataListener[length];
            String[] symbolArray = hbAddSymbols.toArray(new String[length]);
            Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(Source.Huobi);

            int i = 0;
            for (String symbol : hbAddSymbols) {
                Instrument instrument = instruments.get(symbol.toUpperCase());
                tradeListeners[i] = new HuobiInstrumentTrade(instrument, this);
                i++;
            }

            String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
            String tradeFmt = getString(hbMap, C.HUOBI_TRADE_FMT);
            HuobiTradeHandler tradeHandler = new HuobiTradeHandler(wsUrl, tradeFmt, symbolArray, tradeListeners);
            startWebSocket(tradeWsDaemons, Source.Huobi, tradeHandler);
        }
    }

    private void setupBinance(Map<String, Instrument> instruments, Set<String> symbols, Set<String> bnAddSymbols,
            Set<String> bnRmSymbols) {
        if (bnAddSymbols.size() == 0 && bnRmSymbols.size() == 0) {
            return;
        }

        BinanceInstrumentDepth[] binanceListeners = new BinanceInstrumentDepth[symbols.size()];
        BinanceInstrumentTrade[] tradeListeners = new BinanceInstrumentTrade[symbols.size()];
        Map<Object, Object> binanceMap = (Map<Object, Object>) cfg.get(Source.Binance);

        int idx = 0;
        for (String symbol : symbols) {
            Instrument instrument = instruments.get(symbol.toUpperCase());

            IOrderBook binanceBook = makesureOrderBook(Source.Binance, instrument.asLong());
            binanceListeners[idx] = new BinanceInstrumentDepth(instrument, binanceBook, this);
            // String binanceSnapshotUrl = String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10",
            // strInst);
            String binanceSnapshotUrl = String.format(getString(binanceMap, C.BINANCE_REST_URL), symbol.toUpperCase());
            executor.submit(new RestSnapshotRunnable(binanceSnapshotUrl, "GET", null, null, binanceListeners[idx]));
            tradeListeners[idx] = new BinanceInstrumentTrade(instrument, this);

            // 确认聚合订单簿及聚合线程开始工作。
            makesureAggregateOrderBook(instrument);
            idx++;
        }

        String[] symbolArray = symbols.toArray(new String[symbols.size()]);
        BaseWebSocketHandler binanceDepthHandler = new BinanceDepthHandler(getString(binanceMap, C.BINANCE_WS_DEPTH),
                symbolArray, binanceListeners);
        BaseWebSocketHandler binanceTradeHandler = new BinanceTradeHandler(getString(binanceMap, C.BINANCE_WS_TRADE),
                symbolArray, tradeListeners);
        // 币安无法增量订阅或退订，必须重设。
        if (depthWsDaemons.containsKey(Source.Binance)) {
            depthWsDaemons.get(Source.Binance).close(0, "reset");
        }
        if (tradeWsDaemons.containsKey(Source.Binance)) {
            tradeWsDaemons.get(Source.Binance).close(0, "reset");
        }
        startWebSocket(depthWsDaemons, Source.Binance, binanceDepthHandler);
        startWebSocket(tradeWsDaemons, Source.Binance, binanceDepthHandler);
    }

    public void checkOffset() {

    }

    public static String getString(Map<Object, Object> map, Object key) {
        return (String) map.get(key);
    }

    public static int getInt(Map<Object, Object> map, Object key, int def) {
        Integer i = (Integer) map.get(key);
        if (i == null) {
            return def;
        }
        return i.intValue();
    }

    public static long getLong(Map<Object, Object> map, Object key, long def) {
        Long i = (Long) map.get(key);
        if (i == null) {
            return def;
        }
        return i.longValue();
    }

    class Monitor extends Thread {

        public void run() {
            try {
                while (true) {
                    Thread.sleep(C.MONITOR_SLEEP_MILLIS);
                    depthWsDaemons.forEach((k, v) -> v.keepAlive());
                    tradeWsDaemons.forEach((k, v) -> v.keepAlive());
                    checkOffset();

                    printSymbol(new Instrument("XEMBTC", 8, 8));
                }
            } catch (Exception ex) {
                logger.warn("monitor exception: {}", ex.getMessage());
            }
        }
    }
}