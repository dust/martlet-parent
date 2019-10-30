package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.kmfrog.martlet.feed.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.feed.net.FeedBroadcast;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Hello world!
 *
 */
public class App implements Controller {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("MartletAppExecutor-%d").build());

    /** 所有交易对的聚合订单表。 **/
    private final Long2ObjectArrayMap<AggregateOrderBook> aggBooks;

    /** 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿。 **/
    private final Map<Source, Long2ObjectArrayMap<IOrderBook>> multiSrcBooks;

    /** 聚合工作线程。{instrument.asLong : worker} **/
    private final Map<Long, InstrumentAggregation> aggWorkers;

    /** 深度websocket集合。来源为key. **/
    private final Map<Source, WebSocketDaemon> depthWsDaemons;
    
    /** trade流websocket集合。`key`值为来源 **/
    private final Map<Source, WebSocketDaemon> tradeWsDaemons;

    /** 深度ZMQ广播 **/
    private final FeedBroadcast depthBroadcast;
    
    /** 实时交易广播 **/
    private final FeedBroadcast tradeBroadcast;

    public App() {
        depthBroadcast = new FeedBroadcast("localhost", 5188, 1);
        tradeBroadcast = new FeedBroadcast("localhost", 5288, 1);

        multiSrcBooks = new ConcurrentHashMap<>();
        aggBooks = new Long2ObjectArrayMap<>();
        aggWorkers = new ConcurrentHashMap<>();
        
        tradeWsDaemons = new ConcurrentHashMap<>();
        depthWsDaemons = new ConcurrentHashMap<>();
    }

    /**
     * 获得某个来源，指定`instrument`的订单簿。
     * @param src
     * @param instrument
     * @return
     */
    IOrderBook makesureOrderBook(Source src, long instrument) {
        Long2ObjectArrayMap<IOrderBook> srcBooks = multiSrcBooks.computeIfAbsent(src, (key) -> {
            Long2ObjectArrayMap<IOrderBook> sameSrcBooks = new Long2ObjectArrayMap<>();
            sameSrcBooks.put(instrument, new OrderBook(instrument));
            return sameSrcBooks;
        });
        return srcBooks.computeIfAbsent(instrument, (key) -> new OrderBook(key));
    }

    /**
     * 获得指·`instrument`的聚合订单簿。
     * @param instrument
     * @return
     */
    AggregateOrderBook makesureAggregateOrderBook(Instrument instrument) {
        AggregateOrderBook book = aggBooks.computeIfAbsent(instrument.asLong(), (key) -> new AggregateOrderBook(key));
        if (!aggWorkers.containsKey(instrument.asLong())) {
            InstrumentAggregation worker = new InstrumentAggregation(instrument, book, depthBroadcast, this);
            aggWorkers.put(instrument.asLong(), worker);
            worker.start();
        }
        return book;
    }

    void startSnapshotTask(String url, SnapshotDataListener listener) {
        Runnable r = new RestSnapshotRunnable(url, "GET", null, null, listener);
        executor.submit(r);
    }

    void startWebSocket(Source source, BaseWebSocketHandler handler) {
        WebSocketDaemon wsDaemon = new WebSocketDaemon(handler);
        depthWsDaemons.put(source, wsDaemon);
        wsDaemon.keepAlive();
    }

    /**
     * 开始多来源的深度接收。
     * @param instruments 多交易对及其配置。
     */
    void startDepth(Instrument[] instruments){
        String[] binanceSymbolNames = new String[instruments.length];
        BinanceInstrumentDepth[] binanceListeners = new BinanceInstrumentDepth[instruments.length];

        String[] huobiSymbolNames = new String[instruments.length];
        WsDataListener[] huobiListeners = new WsDataListener[instruments.length];

        String[] okexSymbolNames = new String[instruments.length];
        WsDataListener[] okexListeners = new WsDataListener[instruments.length];


        for (int i=0; i<instruments.length; i++) {
            Instrument instrument = instruments[i];
            long lngInst = instrument.asLong();
            String strInst = instrument.asString();

            huobiSymbolNames[i] = strInst;
            IOrderBook huobiBook = makesureOrderBook(Source.Huobi, lngInst);
            huobiListeners[i]= new HuobiInstrumentDepth(instrument, huobiBook, Source.Huobi, this);

            okexSymbolNames[i] = getOkexSymbol(instrument);
            IOrderBook okexBook = makesureOrderBook(Source.Okex, lngInst);
            okexListeners[i] = new OkexInstrumentDepth(instrument, okexBook, Source.Okex, this);


            binanceSymbolNames[i] = strInst;
            IOrderBook binanceBook = makesureOrderBook(Source.Binance, lngInst);
            binanceListeners[i] = new BinanceInstrumentDepth(instrument, binanceBook, Source.Binance, this);
            String binanceSnapshotUrl = String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10", strInst);
            executor.submit(new RestSnapshotRunnable(binanceSnapshotUrl, "GET", null, null, binanceListeners[i]));
        }

        BaseWebSocketHandler binanceDepthHandler = new BinanceDepthHandler("wss://stream.binance.com:9443/stream?streams=%s@depth", binanceSymbolNames, binanceListeners);
        WebSocketDaemon binanceDepthWs = new WebSocketDaemon(binanceDepthHandler);
        depthWsDaemons.put(Source.Binance, binanceDepthWs);
        binanceDepthWs.keepAlive();

        BaseWebSocketHandler huobiDepthHandler = new HuobiDepthHandler(huobiSymbolNames, huobiListeners);
        WebSocketDaemon huobiDepthWs = new WebSocketDaemon(huobiDepthHandler);
        depthWsDaemons.put(Source.Huobi, huobiDepthWs);
        huobiDepthWs.keepAlive();

        BaseWebSocketHandler okexDepthHandler = new OkexDepthHandler(okexSymbolNames, okexListeners);
        WebSocketDaemon okexDepthWs = new WebSocketDaemon(okexDepthHandler);
        depthWsDaemons.put(Source.Okex, okexDepthWs);
        okexDepthWs.keepAlive();


    }

    static String getOkexSymbol(Instrument instrument){
        String symbol = instrument.asString().toUpperCase();
        int length = symbol.length();
        if(symbol.endsWith("USDT")){
            return String.format("%s-USDT", symbol.substring(0, length -4));
        }
        else if(symbol.endsWith("XRP") || symbol.endsWith("BTC") || symbol.endsWith("ETH")){  // ABCXRP, 0
            return String.format("%s-%s", symbol.substring(0, length -3), symbol.substring(length -3, length));
        }
        throw new IllegalArgumentException("illegal argument: "+instrument.asString());

    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        App app = new App();
        Instrument bnbbtc = new Instrument("BTCUSDT", 8, 8);
        Instrument bnbeth = new Instrument("ETHUSDT", 8, 8);
        // app.makesureAggregateOrderBook(bnbbtc);
        // IOrderBook btcBook = app.makesureOrderBook(Source.Binance, bnbbtc.asLong());

        // BinanceInstrumentDepth btc = new BinanceInstrumentDepth(bnbbtc, btcBook, Source.Binance, app);
        // BinanceInstrumentDepth eth = new BinanceInstrumentDepth(bnbeth, bnbethBook, Source.Binance, app);
        // app.startSnapshotTask(String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10", "BTCUSDT"),
        // btc);
        // app.startSnapshotTask("BNBETH", eth);
        // BaseWebSocketHandler handler = new BinanceWebSocketHandler(
        // "wss://stream.binance.com:9443/stream?streams=%s@depth", new String[] { "btcusdt" },
        // new BinanceInstrumentDepth[] { btc });
        // app.startWebSocket(Source.Binance, handler);

        BaseInstrumentTrade btcTrade = new BinanceInstrumentTrade(bnbbtc, Source.Binance, app);
        BaseWebSocketHandler handler = new BinanceTradeHandler(
                "wss://stream.binance.com:9443/stream?streams=%s@aggTrade", new String[] { "ethusdt" },
                new BaseInstrumentTrade[] { btcTrade });
        app.startWebSocket(Source.Binance, handler);

        // Instrument btcusdt = new Instrument("BTCUSDT", 8, 8);
        // AggregateOrderBook btcBook = app.makesureOrderBook(btcusdt.asLong());
        //
        IOrderBook hbBtcUsdt = app.makesureOrderBook(Source.Huobi, bnbbtc.asLong());
        HuobiInstrumentDepth hbBtc = new HuobiInstrumentDepth(bnbbtc, hbBtcUsdt, Source.Huobi, app);
        BaseWebSocketHandler hbHandler = new HuobiDepthHandler(new String[] { "btcusdt" },
                new HuobiInstrumentDepth[] { hbBtc });
        app.startWebSocket(Source.Huobi, hbHandler);

        // Instrument xie = new Instrument("XIEPTCN", 4, 4);
        // AggregateOrderBook xieBook = app.makesureOrderBook(xie.asLong());
        // BhexInstrumentDepth xieDepth = new BhexInstrumentDepth(xie, xieBook, Source.Bhex, app);
        // BaseWebSocketHandler hbexHandler = new BhexWebSocketHandler(new String[] {"XIEPTCN"}, new
        // BhexInstrumentDepth[] {xieDepth});
        // app.startWebSocket(Source.Bhex, hbexHandler);
        IOrderBook okexBtcUsdt = app.makesureOrderBook(Source.Okex, bnbbtc.asLong());
        OkexInstrumentDepth okexDepth = new OkexInstrumentDepth(bnbbtc, okexBtcUsdt, Source.Okex, app);
        OkexDepthHandler okexHandler = new OkexDepthHandler(new String[] { "BTC-USDT" },
                new OkexInstrumentDepth[] { okexDepth });
        app.startWebSocket(Source.Okex, okexHandler);

        while (true) {
            Thread.sleep(10000L);
            // btcBook.dump(Side.BUY, System.out);
            handler.dumpStats(System.out);
            // long now = System.currentTimeMillis();
            // System.out.format("\nBA: %d|%d\n", now - btcBook.getLastReceivedTs(), btcBook.getLastReceivedTs() -
            // btcBook.getLastUpdateTs());
            System.out.println("\n#####\n");

            // app.websocketDaemons.get(Source.Okex).keepAlive();
            // okexBtcUsdt.dump(Side.BUY, System.out);
            // okexHandler.dumpStats(System.out);
            // now = System.currentTimeMillis();
            // System.out.format("\nOK: %d|%d\n", now - okexBtcUsdt.getLastReceivedTs(), okexBtcUsdt.getLastReceivedTs()
            // - okexBtcUsdt.getLastUpdateTs());
            System.out.println("\n====\n");

            // xieBook.dump(Side.BUY, System.out);
            // app.websocketDaemons.get(Source.Bhex).keepAlive();

            // hbBtcUsdt.dump(Side.BUY, System.out);
            // hbHandler.dumpStats(System.out);
            // app.websocketDaemons.get(Source.Huobi).keepAlive();
            // now = System.currentTimeMillis();
            // System.out.format("\nHB: %d|%d\n", now - hbBtcUsdt.getLastReceivedTs(), hbBtcUsdt.getLastReceivedTs() -
            // hbBtcUsdt.getLastUpdateTs());
            System.out.println("\n====\n");

            // executor.submit(new AggregateRunnable(app.makesureAggregateOrderBook(bnbbtc.asLong()), new Source[]
            // {Source.Binance, Source.Okex, Source.Huobi}, app));
            // if (app.aggWorkers.containsKey(bnbbtc.asLong())) {
            // app.aggWorkers.get(bnbbtc.asLong()).dumpStats(System.out);
            // }
            // System.out.println("\n====\n");
            // AggregateOrderBook aggBook = app.makesureAggregateOrderBook(bnbbtc);
            // System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", btcBook.getBestBidPrice(), btcBook.getBestAskPrice(),
            // hbBtcUsdt.getBestBidPrice(), hbBtcUsdt.getBestAskPrice(), okexBtcUsdt.getBestBidPrice(),
            // okexBtcUsdt.getBestAskPrice(), aggBook.getBestBidPrice(), aggBook.getBestAskPrice());
            // System.out.format("\n\n%s\n", aggBook.dumpPlainText(Side.BUY, 8, 8, 5));
        }
    }

    

    public void reset(Source mkt, Instrument instrument, BaseInstrumentDepth depth, boolean isSubscribe,
            boolean isConnect) {
        // this.startSnapshotTask(instrument.asString().toUpperCase(), depth);
        depthWsDaemons.get(mkt).reset(instrument, depth, isSubscribe, isConnect);
    }

    
    public void resetBook(Source mkt, Instrument instrument, IOrderBook book) {
        try {
            if (aggWorkers.containsKey(instrument.asLong())) {
                aggWorkers.get(instrument.asLong()).putMsg(mkt, book);
            }
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void logTrade(Source src, Instrument instrument, long id, long price, long volume, long cnt, long isBuy,
            long ts) {
        System.out.format("%s|%s, %d@%d\n", src.name(), instrument.asString(), price, volume);

    }

    @Override
    public void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk,
            long lastUpdate, long lastReceived) {
    }

}
