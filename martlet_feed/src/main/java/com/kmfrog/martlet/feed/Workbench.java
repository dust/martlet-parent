package com.kmfrog.martlet.feed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.impl.BhexDepthHandler;
import com.kmfrog.martlet.feed.impl.BhexInstrumentDepth;
import com.kmfrog.martlet.feed.impl.BikunDepthHandler;
import com.kmfrog.martlet.feed.impl.BikunInstrumentDepth;
import com.kmfrog.martlet.feed.impl.LoexDepthHandler;
import com.kmfrog.martlet.feed.impl.LoexInstrumentDepth;
import com.kmfrog.martlet.feed.net.FeedBroadcast;
import com.typesafe.config.Config;

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

    // private final Map<Source, Long2ObjectArrayMap<RollingTimeSpan<TradeLog>>> multiSrcAvgTrades;

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
    
    private final Map<Source, RestDaemon> restDaemons;

    /**
     * 深度ZMQ广播
     **/
    private final FeedBroadcast depthFeed;

    /**
     * 实时交易广播
     **/
    // private final FeedBroadcast tradeFeed;

    /** 状态监控线程 **/
    private final Monitor monitor;
    Config cfg;

    public Workbench(Config cfg) {
        this.cfg = cfg;

        String depthHost = getString(cfg, C.PUB_DEPTH_HOST);
        int depthPort = getInt(cfg, C.PUB_DEPTH_PORT, 5188);
        int depthIoThreads = getInt(cfg, C.PUB_DEPTH_IO_THREAD_CNT, 2);
        depthFeed = new FeedBroadcast(depthHost, depthPort, depthIoThreads);

        // String tradeHost = getString(cfg, C.PUB_TRADE_HOST);
        // int tradePort = getInt(cfg, C.PUB_TRADE_PORT, 5288);
        // int tradeIoThreads = getInt(cfg, C.PUB_TRADE_IO_THREAD_CNT, 2);
        // tradeFeed = new FeedBroadcast(tradeHost, tradePort, tradeIoThreads);

        depthPusher = new Pusher(depthFeed, this);
        // tradePusher = new Pusher(tradeFeed, this);
        monitor = new Monitor();

        depthPusher.start();
        // tradePusher.start();
        monitor.start();

        multiSrcBooks = new ConcurrentHashMap<>();
        // multiSrcAvgTrades = new ConcurrentHashMap<>();
        aggBooks = new Long2ObjectArrayMap<>();
        aggWorkers = new ConcurrentHashMap<>();

        tradeWsDaemons = new ConcurrentHashMap<>();
        depthWsDaemons = new ConcurrentHashMap<>();
        restDaemons = new ConcurrentHashMap<>();
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

    // RollingTimeSpan<TradeLog> makesureRollingAvgTrade(Source src, long instrument) {
    // Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> srcAvgTrades = multiSrcAvgTrades.computeIfAbsent(src, (key) -> {
    // Long2ObjectArrayMap<RollingTimeSpan<TradeLog>> sameSrcAvgTrades = new Long2ObjectArrayMap<>();
    // sameSrcAvgTrades.put(instrument, new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS));
    // return sameSrcAvgTrades;
    // });
    // return srcAvgTrades.computeIfAbsent(instrument,
    // (key) -> new RollingTimeSpan<TradeLog>(C.TRADE_AVG_WINDOW_MILLIS));
    // }

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
        Runnable r = new RestSnapshotRunnable("", url, "GET", null, null, listener);
        executor.submit(r);
    }

    public Future<?> submitTask(Runnable r) {
        return executor.submit(r);
    }

    static void startWebSocket(Map<Source, WebSocketDaemon> daemons, Source source, BaseWebSocketHandler handler) {
        WebSocketDaemon wsDaemon = new WebSocketDaemon(handler);
        daemons.put(source, wsDaemon);
        wsDaemon.keepAlive();
    }
    
    static void startRestDepth(Map<Source, RestDaemon> daemons, Source source, LoexDepthHandler handler, Controller app) {
        RestDaemon restDaemon = new RestDaemon(source, handler, app);
        daemons.put(source, restDaemon);
        restDaemon.start();
    }

    private void printSymbol(Instrument instrument) {
        // AggregateOrderBook aggBook = makesureAggregateOrderBook(instrument);
        IOrderBook binanceBook = makesureOrderBook(Source.Binance, instrument.asLong());
        IOrderBook hbBook = makesureOrderBook(Source.Huobi, instrument.asLong());
        IOrderBook okexBook = makesureOrderBook(Source.Okex, instrument.asLong());
        IOrderBook bhexBook = makesureOrderBook(Source.Bhex, instrument.asLong());

        // System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", binanceBook.getBestBidPrice(),
        // binanceBook.getBestAskPrice(), hbBook.getBestBidPrice(), hbBook.getBestAskPrice(),
        // okexBook.getBestBidPrice(), okexBook.getBestAskPrice(), aggBook.getBestBidPrice(),
        // aggBook.getBestAskPrice());
        // System.out.format("\n\n%s\n", aggBook.getOriginText(Source.Mix, 5));
        System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", binanceBook.getBestBidPrice(),
                binanceBook.getBestAskPrice(), hbBook.getBestBidPrice(), hbBook.getBestAskPrice(),
                okexBook.getBestBidPrice(), okexBook.getBestAskPrice(), bhexBook.getBestBidPrice(),
                bhexBook.getBestAskPrice());
        System.out.format("\n\n%s\n", bhexBook.getOriginText(Source.Bhex, 5));
        // RollingTimeSpan<TradeLog> hbAvg = makesureRollingAvgTrade(Source.Huobi, instrument.asLong());
        // System.out.format("%d|%d|%d", hbAvg.avg(), hbAvg.first(), hbAvg.last());
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
                multiSrcBooks.get(mkt).put(instrument.asLong(), book);
                String originText = book.getOriginText(mkt, C.MAX_LEVEL);
                // System.out.println(originText);
                depthPusher.put(originText);
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
        // RollingTimeSpan<TradeLog> avgTrade = makesureRollingAvgTrade(src, instrument.asLong());
        // avgTrade.add(log);

    }

    @Override
    public void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk,
            long lastUpdate, long lastReceived) {
    }

    public void start(Map<String, List<Instrument>> supportedInstruments) {
        setupBhex(supportedInstruments.get(Source.Bhex.name()));
        setupBikun(supportedInstruments.get(Source.Bikun.name()));
        setupLoex(supportedInstruments.get(Source.Loex.name()));
    }
    
//    void setupLoex(List<Instrument> instruments) {
//    	String baseUrl = cfg.getString(C.LOEX_REST_URL);
//    	int size = instruments.size();
//    	WsDataListener[] listeners = new LoexInstrumentDepth[size];
//    	String[] instrumentArr = new String[size];
//    	for(int i=0; i<size; i++) {
//    		Instrument instrument = instruments.get(i);
//    		logger.info("{}:{}:{}", Source.Loex.name(), instrument.asString(), instrument.asLong());
//    		instrumentArr[i] = instrument.asString();
//    		IOrderBook book = makesureOrderBook(Source.Loex, instrument.asLong());
//    		try {
//    			listeners[i] = new LoexInstrumentDepth(instrument, book, Source.Loex, this);
//    		}catch(Exception ex) {
//    			System.out.println("111"+instrument);
//    			System.out.println("222"+book);
//    			System.out.println("333"+this);
//    			ex.printStackTrace();
//    		}
//    	}
//    	
//    	BaseApiRestClient client = new LoexApiRestClient(baseUrl, cfg.getString("api.key.loex"), cfg.getString("api.key.loex"));
//    	MktDepthTracker mktDepthTracker = new MktDepthTracker(Source.Loex, instrumentArr, listeners, client, 5000);
//    	mktDepthTracker.start();
//    }

    private void setupLoex(List<Instrument> list) {
        String depthUrl = "https://openapi.loex.io//open/api/market_dept?symbol=%s&type=step0";
        int size = list.size();
        SnapshotDataListener[] listeners = new LoexInstrumentDepth[size];
        String[] instrumentArr = new String[size];
        
        for(int i=0; i<size; i++) {
            Instrument instrument = list.get(i);
            instrumentArr[i] = instrument.asString().toLowerCase();
            IOrderBook book = makesureOrderBook(Source.Loex, instrument.asLong());
            listeners[i] = new LoexInstrumentDepth(instrument, book, Source.Loex, this);
        }
        
        LoexDepthHandler handler = new LoexDepthHandler(depthUrl, instrumentArr, listeners);
        startRestDepth(restDaemons, Source.Loex, handler, this);
    }

    void setupBikun(List<Instrument> instruments) {
        String wsUrl = cfg.getString(C.BIKUN_WS_URL);
        String depthFmt = cfg.getString(C.BIKUN_DEPTH_FMT);

        int size = instruments.size();
        WsDataListener[] listeners = new BikunInstrumentDepth[size];
        String[] instrumentArr = new String[size];

        for (int i = 0; i < size; i++) {
            Instrument instrument = instruments.get(i);
            logger.info("{}:{}:{}", Source.Bikun.name(), instrument.asString(), instrument.asLong());
            instrumentArr[i] = instrument.asString();
            IOrderBook book = makesureOrderBook(Source.Bikun, instrument.asLong());
            listeners[i] = new BikunInstrumentDepth(instrument, book, Source.Bikun, this);
        }

        BikunDepthHandler handler = new BikunDepthHandler(wsUrl, depthFmt, instrumentArr, listeners);
        startWebSocket(depthWsDaemons, Source.Bikun, handler);

    }

    void setupBhex(List<Instrument> instruments) {
        String wsUrl = cfg.getString(C.BHEX_WS_URL);
        String depthFmt = cfg.getString(C.BHEX_DEPTH_FMT);
        int size = instruments.size();
        WsDataListener[] listeners = new BhexInstrumentDepth[size];
        String[] instrumentArr = new String[size];
        // Set<String> symbols = instruments.stream().map(instrument -> instrument.asString())
        // .collect(Collectors.toSet());
        for (int i = 0; i < size; i++) {
            Instrument instrument = instruments.get(i);
            logger.info("{}:{}", instrument.asString(), instrument.asLong());
            instrumentArr[i] = instrument.asString();
            IOrderBook book = makesureOrderBook(Source.Bhex, instrument.asLong());
            listeners[i] = new BhexInstrumentDepth(instrument, book, this);
        }

        BhexDepthHandler handler = new BhexDepthHandler(wsUrl, depthFmt, instrumentArr, listeners);
        startWebSocket(depthWsDaemons, Source.Bhex, handler);
    }

    // public void start(List<Instrument> supportedInstruments, Set<String> binanceSymbols, Set<String> huobiSymbols,
    // Set<String> okexSymbols) {
    // Set<String> all =
    // Arrays.asList(supportedInstruments).stream().map(instrument->instrument.asString()).collect(Collectors.toSet());
    // Set<String> bnSymbols = Sets.intersection(all, binanceSymbols);
    // Set<String> hbSymbols = Sets.intersection(all, huobiSymbols);
    // Set<String> okSymbols = Sets.intersection(all, okexSymbols);
    // Map<String, Instrument> all = supportedInstruments.stream()
    // .collect(Collectors.toMap(Instrument::asString, instrument -> instrument));
    // Set<String> bnExistSymbols = depthWsDaemons.get(Source.Binance) == null ? new HashSet<>()
    // : depthWsDaemons.get(Source.Binance).getSymbolNames();
    // Set<String> hbExistSymbols = depthWsDaemons.get(Source.Huobi) == null ? new HashSet<>()
    // : depthWsDaemons.get(Source.Huobi).getSymbolNames();
    // Set<String> okExistSymbols = depthWsDaemons.get(Source.Okex) == null ? new HashSet<>()
    // : depthWsDaemons.get(Source.Okex).getSymbolNames();
    // Set<String> bnAddSymbols = Sets.difference(binanceSymbols, bnExistSymbols);
    // Set<String> bnRmSymbols = Sets.difference(bnExistSymbols, binanceSymbols);
    // Set<String> hbAddSymbols = Sets.difference(huobiSymbols, hbExistSymbols);
    // Set<String> okAddSymbols = Sets.difference(okexSymbols, okExistSymbols);
    // Set<String> hbRmSymbols = Sets.difference(hbExistSymbols, huobiSymbols);
    // Set<String> okRmSymbols = Sets.difference(okExistSymbols, okexSymbols);
    //
    //// setupBinance(all, binanceSymbols, bnAddSymbols, bnRmSymbols);
    //// setupHuobi(all, hbAddSymbols, hbRmSymbols);
    //// setupOkex(all, okAddSymbols, okRmSymbols);
    // }

    // private void setupOkex(Map<String, Instrument> instruments, Set<String> okAddSymbols, Set<String> okRmSymbols) {
    // int length = okAddSymbols.size();
    // if (length == 0 && okRmSymbols.size() == 0) {
    // return;
    // }
    //
    // Source okex = Source.Huobi;
    // if (depthWsDaemons.containsKey(okex)) {
    // // 增量
    // WebSocketDaemon ws = depthWsDaemons.get(okex);
    // for (String symbol : okAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // IOrderBook book = makesureOrderBook(okex, instrument.asLong());
    // ws.subscribeSymbol(symbol, new HuobiInstrumentDepth(instrument, book, this));
    // }
    //
    // for (String symbol : okRmSymbols) {
    // ws.unsubscribeSymbols(symbol);
    // }
    // }
    // if (tradeWsDaemons.containsKey(okex)) {
    // // 增量
    // WebSocketDaemon ws = tradeWsDaemons.get(okex);
    // for (String symbol : okAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // ws.subscribeSymbol(symbol, new HuobiInstrumentTrade(instrument, this));
    // }
    //
    // for (String symbol : okRmSymbols) {
    // ws.unsubscribeSymbols(symbol);
    // }
    // }
    //
    // if (!depthWsDaemons.containsKey(okex)) {
    // // 首次连接websocket
    // WsDataListener[] depthListeners = new WsDataListener[length];
    // String[] symbolArray = okAddSymbols.toArray(new String[length]);
    // Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(okex);
    //
    // int i = 0;
    // for (String symbol : okAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // IOrderBook book = makesureOrderBook(okex, instrument.asLong());
    // depthListeners[i] = new HuobiInstrumentDepth(instrument, book, this);
    // i++;
    // }
    //
    // String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
    // String depthFmt = getString(hbMap, C.HUOBI_DEPTH_FMT);
    // HuobiDepthHandler handler = new HuobiDepthHandler(wsUrl, depthFmt, symbolArray, depthListeners);
    // startWebSocket(depthWsDaemons, okex, handler);
    // }
    //
    // if (!tradeWsDaemons.containsKey(okex)) {
    // // 首次连接websocket
    // WsDataListener[] tradeListeners = new WsDataListener[length];
    // String[] symbolArray = okAddSymbols.toArray(new String[length]);
    // Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(okex);
    //
    // int i = 0;
    // for (String symbol : okAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // tradeListeners[i] = new HuobiInstrumentTrade(instrument, this);
    // i++;
    // }
    //
    // String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
    // String tradeFmt = getString(hbMap, C.HUOBI_TRADE_FMT);
    // HuobiTradeHandler tradeHandler = new HuobiTradeHandler(wsUrl, tradeFmt, symbolArray, tradeListeners);
    // startWebSocket(tradeWsDaemons, okex, tradeHandler);
    // }
    // }
    //
    // private void setupHuobi(Map<String, Instrument> instruments, Set<String> hbAddSymbols, Set<String> hbRmSymbols) {
    // int length = hbAddSymbols.size();
    // if (length == 0 && hbRmSymbols.size() == 0) {
    // return;
    // }
    //
    // if (depthWsDaemons.containsKey(Source.Huobi)) {
    // // 增量
    // WebSocketDaemon ws = depthWsDaemons.get(Source.Huobi);
    // for (String symbol : hbAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // IOrderBook book = makesureOrderBook(Source.Huobi, instrument.asLong());
    // ws.subscribeSymbol(symbol, new HuobiInstrumentDepth(instrument, book, this));
    // }
    //
    // for (String symbol : hbRmSymbols) {
    // ws.unsubscribeSymbols(symbol);
    // }
    // }
    // if (tradeWsDaemons.containsKey(Source.Huobi)) {
    // // 增量
    // WebSocketDaemon ws = tradeWsDaemons.get(Source.Huobi);
    // for (String symbol : hbAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // ws.subscribeSymbol(symbol, new HuobiInstrumentTrade(instrument, this));
    // }
    //
    // for (String symbol : hbRmSymbols) {
    // ws.unsubscribeSymbols(symbol);
    // }
    // }
    //
    // if (!depthWsDaemons.containsKey(Source.Huobi)) {
    // // 首次连接websocket
    // WsDataListener[] depthListeners = new WsDataListener[length];
    // String[] symbolArray = hbAddSymbols.toArray(new String[length]);
    // Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(Source.Huobi);
    //
    // int i = 0;
    // for (String symbol : hbAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // IOrderBook book = makesureOrderBook(Source.Huobi, instrument.asLong());
    // depthListeners[i] = new HuobiInstrumentDepth(instrument, book, this);
    // i++;
    // }
    //
    // String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
    // String depthFmt = getString(hbMap, C.HUOBI_DEPTH_FMT);
    // HuobiDepthHandler handler = new HuobiDepthHandler(wsUrl, depthFmt, symbolArray, depthListeners);
    // startWebSocket(depthWsDaemons, Source.Huobi, handler);
    // }
    //
    // if (!tradeWsDaemons.containsKey(Source.Huobi)) {
    // // 首次连接websocket
    // WsDataListener[] tradeListeners = new WsDataListener[length];
    // String[] symbolArray = hbAddSymbols.toArray(new String[length]);
    // Map<Object, Object> hbMap = (Map<Object, Object>) cfg.get(Source.Huobi);
    //
    // int i = 0;
    // for (String symbol : hbAddSymbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    // tradeListeners[i] = new HuobiInstrumentTrade(instrument, this);
    // i++;
    // }
    //
    // String wsUrl = getString(hbMap, C.HUOBI_WS_URL);
    // String tradeFmt = getString(hbMap, C.HUOBI_TRADE_FMT);
    // HuobiTradeHandler tradeHandler = new HuobiTradeHandler(wsUrl, tradeFmt, symbolArray, tradeListeners);
    // startWebSocket(tradeWsDaemons, Source.Huobi, tradeHandler);
    // }
    // }
    //
    // private void setupBinance(Map<String, Instrument> instruments, Set<String> symbols, Set<String> bnAddSymbols,
    // Set<String> bnRmSymbols) {
    // if (bnAddSymbols.size() == 0 && bnRmSymbols.size() == 0) {
    // return;
    // }
    //
    // BinanceInstrumentDepth[] binanceListeners = new BinanceInstrumentDepth[symbols.size()];
    // BinanceInstrumentTrade[] tradeListeners = new BinanceInstrumentTrade[symbols.size()];
    // Map<Object, Object> binanceMap = (Map<Object, Object>) cfg.get(Source.Binance);
    //
    // int idx = 0;
    // for (String symbol : symbols) {
    // Instrument instrument = instruments.get(symbol.toUpperCase());
    //
    // IOrderBook binanceBook = makesureOrderBook(Source.Binance, instrument.asLong());
    // binanceListeners[idx] = new BinanceInstrumentDepth(instrument, binanceBook, this);
    // // String binanceSnapshotUrl = String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10",
    // // strInst);
    // String binanceSnapshotUrl = String.format(getString(binanceMap, C.BINANCE_REST_URL), symbol.toUpperCase());
    // executor.submit(new RestSnapshotRunnable(binanceSnapshotUrl, "GET", null, null, binanceListeners[idx]));
    // tradeListeners[idx] = new BinanceInstrumentTrade(instrument, this);
    //
    // // 确认聚合订单簿及聚合线程开始工作。
    // makesureAggregateOrderBook(instrument);
    // idx++;
    // }
    //
    // String[] symbolArray = symbols.toArray(new String[symbols.size()]);
    // BaseWebSocketHandler binanceDepthHandler = new BinanceDepthHandler(getString(binanceMap, C.BINANCE_WS_DEPTH),
    // symbolArray, binanceListeners);
    // BaseWebSocketHandler binanceTradeHandler = new BinanceTradeHandler(getString(binanceMap, C.BINANCE_WS_TRADE),
    // symbolArray, tradeListeners);
    // // 币安无法增量订阅或退订，必须重设。
    // if (depthWsDaemons.containsKey(Source.Binance)) {
    // depthWsDaemons.get(Source.Binance).close(0, "reset");
    // }
    // if (tradeWsDaemons.containsKey(Source.Binance)) {
    // tradeWsDaemons.get(Source.Binance).close(0, "reset");
    // }
    // startWebSocket(depthWsDaemons, Source.Binance, binanceDepthHandler);
    // startWebSocket(tradeWsDaemons, Source.Binance, binanceDepthHandler);
    // }

    public void checkOffset() {

    }

    public static String getString(Config cfg, String key) {
        return cfg.getString(key);
    }

    public static int getInt(Config cfg, String key, int def) {
        Integer i = cfg.getInt(key);
        // if (i == null) {
        // return def;
        // }
        return i.intValue();
    }

    public static long getLong(Config cfg, String key, long def) {
        Long i = cfg.getLong(key);
        // if (i == null) {
        // return def;
        // }
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

                    printSymbol(new Instrument("HNTCBTC", 8, 3, 8));
                }
            } catch (Exception ex) {
                logger.warn("monitor exception: {}", ex.getMessage());
            }
        }
    }
}