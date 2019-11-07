package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.C;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.RollingTimeSpan;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.impl.BinanceDepthHandler;
import com.kmfrog.martlet.feed.impl.BinanceInstrumentDepth;
import com.kmfrog.martlet.feed.impl.BinanceInstrumentTrade;
import com.kmfrog.martlet.feed.impl.BinanceTradeHandler;
import com.kmfrog.martlet.feed.impl.HuobiDepthHandler;
import com.kmfrog.martlet.feed.impl.HuobiInstrumentDepth;
import com.kmfrog.martlet.feed.impl.HuobiInstrumentTrade;
import com.kmfrog.martlet.feed.impl.HuobiTradeHandler;
import com.kmfrog.martlet.feed.impl.OkexDepthHandler;
import com.kmfrog.martlet.feed.impl.OkexInstrumentDepth;
import com.kmfrog.martlet.feed.impl.OkexInstrumentTrade;
import com.kmfrog.martlet.feed.impl.OkexTradeHandler;
import com.kmfrog.martlet.feed.net.FeedBroadcast;
import com.kmfrog.martlet.util.ASCII;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Hello world!
 */
public class App implements Controller {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

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
    
    
    //test
    private final RollingTimeSpan<TradeLog> hbTradeSpan = new RollingTimeSpan<>(10000);

    public App() {
        depthFeed = new FeedBroadcast("localhost", 5188, 1);
        tradeFeed = new FeedBroadcast("localhost", 5288, 1);
        depthPusher = new Pusher(depthFeed, this);
        tradePusher = new Pusher(tradeFeed, this);
        depthPusher.start();
        tradePusher.start();

        multiSrcBooks = new ConcurrentHashMap<>();
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

    /**
     * 开始多来源的深度接收。
     *
     * @param instruments 多交易对及其配置。
     */
    void startDepth(Instrument[] instruments) {
        String[] binanceSymbolNames = new String[instruments.length];
        BinanceInstrumentDepth[] binanceListeners = new BinanceInstrumentDepth[instruments.length];

        String[] huobiSymbolNames = new String[instruments.length];
        WsDataListener[] huobiListeners = new WsDataListener[instruments.length];

        String[] okexSymbolNames = new String[instruments.length];
        WsDataListener[] okexListeners = new WsDataListener[instruments.length];


        for (int i = 0; i < instruments.length; i++) {
            Instrument instrument = instruments[i];
            long lngInst = instrument.asLong();
            String strInst = instrument.asString();

            huobiSymbolNames[i] = strInst.toLowerCase();
            IOrderBook huobiBook = makesureOrderBook(Source.Huobi, lngInst);
            huobiListeners[i] = new HuobiInstrumentDepth(instrument, huobiBook, this);

            okexSymbolNames[i] = getOkexSymbol(instrument);
            IOrderBook okexBook = makesureOrderBook(Source.Okex, lngInst);
            okexListeners[i] = new OkexInstrumentDepth(instrument, okexBook, this);


            binanceSymbolNames[i] = strInst.toLowerCase();
            IOrderBook binanceBook = makesureOrderBook(Source.Binance, lngInst);
            binanceListeners[i] = new BinanceInstrumentDepth(instrument, binanceBook, this);
            String binanceSnapshotUrl = String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10", strInst);
            executor.submit(new RestSnapshotRunnable(binanceSnapshotUrl, "GET", null, null, binanceListeners[i]));

            //确认聚合订单簿及聚合线程开始工作。
            makesureAggregateOrderBook(instrument);
        }

        BaseWebSocketHandler binanceDepthHandler = new BinanceDepthHandler("wss://stream.binance.com:9443/stream?streams=%s@depth", binanceSymbolNames, binanceListeners);
        startWebSocket(depthWsDaemons, Source.Binance, binanceDepthHandler);

        BaseWebSocketHandler huobiDepthHandler = new HuobiDepthHandler(huobiSymbolNames, huobiListeners);
        startWebSocket(depthWsDaemons, Source.Huobi, huobiDepthHandler);

        BaseWebSocketHandler okexDepthHandler = new OkexDepthHandler(okexSymbolNames, okexListeners);
        startWebSocket(depthWsDaemons, Source.Okex, okexDepthHandler);
    }

    void startTrade(Instrument[] instruments) {
        String[] binanceSymbolNames = new String[instruments.length];
        WsDataListener[] binanceListeners = new WsDataListener[instruments.length];

        String[] huobiSymbolNames = new String[instruments.length];
        WsDataListener[] huobiListeners = new WsDataListener[instruments.length];

        String[] okexSymbolNames = new String[instruments.length];
        WsDataListener[] okexListeners = new WsDataListener[instruments.length];

        for (int i = 0; i < instruments.length; i++) {
            Instrument instrument = instruments[i];
            String strInst = instrument.asString();

            huobiSymbolNames[i] = strInst.toLowerCase();
            huobiListeners[i] = new HuobiInstrumentTrade(instrument, this);

            okexSymbolNames[i] = getOkexSymbol(instrument);
            okexListeners[i] = new OkexInstrumentTrade(instrument, this);

            binanceSymbolNames[i] = strInst.toLowerCase();
            binanceListeners[i] = new BinanceInstrumentTrade(instrument, this);
        }

        if (tradePusher != null) {
            tradePusher.quit();
            try {
                tradePusher.join(100);
            } catch (InterruptedException e) {
                logger.warn(e.getMessage(), e);
            }
        }
        tradePusher = new Pusher(tradeFeed, this);
        tradePusher.start();

        BaseWebSocketHandler binanceTradeHandler = new BinanceTradeHandler("wss://stream.binance.com:9443/stream?streams=%s@aggTrade", binanceSymbolNames, binanceListeners);
        startWebSocket(tradeWsDaemons, Source.Binance, binanceTradeHandler);

        BaseWebSocketHandler huobiTradeHandler = new HuobiTradeHandler(huobiSymbolNames, huobiListeners);
        startWebSocket(tradeWsDaemons, Source.Huobi, huobiTradeHandler);

        BaseWebSocketHandler okexTradeHandler = new OkexTradeHandler(okexSymbolNames, okexListeners);
        startWebSocket(tradeWsDaemons, Source.Okex, okexTradeHandler);
    }

    static String getOkexSymbol(Instrument instrument) {
        String symbol = instrument.asString().toUpperCase();
        int length = symbol.length();
        if (symbol.endsWith("USDT")) {
            return String.format("%s-USDT", symbol.substring(0, length - 4));
        } else if (symbol.endsWith("XRP") || symbol.endsWith("BTC") || symbol.endsWith("ETH")) {  // ABCXRP, 0
            return String.format("%s-%s", symbol.substring(0, length - 3), symbol.substring(length - 3, length));
        }
        throw new IllegalArgumentException("illegal argument: " + instrument.asString());

    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        App app = new App();
        Instrument btcUsdt = new Instrument("BTCUSDT", 8, 8);
        Instrument ethusdt = new Instrument("ETHUSDT", 8, 8);
        Instrument[] instruments = new Instrument[]{btcUsdt, ethusdt};
        app.startDepth(instruments);
        app.startTrade(instruments);

        while (true) {
            Thread.sleep(10000L);
            app.depthWsDaemons.get(Source.Binance).keepAlive();
            app.depthWsDaemons.get(Source.Binance).dumpStats(System.out);
            // long now = System.currentTimeMillis();
            // System.out.format("\nBA: %d|%d\n", now - binanceBook.getLastReceivedTs(), binanceBook.getLastReceivedTs() -
            // binanceBook.getLastUpdateTs());
            System.out.println("\n#####\n");

            app.depthWsDaemons.get(Source.Okex).keepAlive();
            app.depthWsDaemons.get(Source.Okex).dumpStats(System.out);

            System.out.println("\n====\n");

            // xieBook.dump(Side.BUY, System.out);
            // app.websocketDaemons.get(Source.Bhex).keepAlive();

            // hbBook.dump(Side.BUY, System.out);
            app.depthWsDaemons.get(Source.Huobi).dumpStats(System.out);
            app.depthWsDaemons.get(Source.Huobi).keepAlive();
            // now = System.currentTimeMillis();
            // System.out.format("\nHB: %d|%d\n", now - hbBook.getLastReceivedTs(), hbBook.getLastReceivedTs() -
            // hbBook.getLastUpdateTs());
            System.out.println("\n====\n");


            if (app.aggWorkers.containsKey(btcUsdt.asLong())) {
                app.aggWorkers.get(btcUsdt.asLong()).dumpStats(System.out);
            }
            System.out.println("\n====\n");
            
            if (app.aggWorkers.containsKey(ethusdt.asLong())) {
                app.aggWorkers.get(ethusdt.asLong()).dumpStats(System.out);
            }
            System.out.println("\n########\n");
            printSymbol(app, btcUsdt);
            printSymbol(app, ethusdt);
            System.out.println("HB.avg" + app.hbTradeSpan.avg() + "|"+app.hbTradeSpan.last()+"|"+app.hbTradeSpan.first());
        }
    }

    private static void printSymbol(App app, Instrument instrument) {
        AggregateOrderBook aggBook = app.makesureAggregateOrderBook(instrument);
        IOrderBook binanceBook = app.makesureOrderBook(Source.Binance, instrument.asLong());
        IOrderBook hbBook = app.makesureOrderBook(Source.Huobi, instrument.asLong());
        IOrderBook okexBook = app.makesureOrderBook(Source.Okex, instrument.asLong());

        System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", binanceBook.getBestBidPrice(), binanceBook.getBestAskPrice(),
                hbBook.getBestBidPrice(), hbBook.getBestAskPrice(), okexBook.getBestBidPrice(),
                okexBook.getBestAskPrice(), aggBook.getBestBidPrice(), aggBook.getBestAskPrice());
        System.out.format("\n\n%s\n", aggBook.getOriginText(Source.Mix, 5));
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
            depthPusher.put(book.getOriginText(mkt, C.MAX_LEVEL));
        } catch (InterruptedException e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void logTrade(Source src, Instrument instrument, long id, long price, long volume, long cnt, boolean isBuy,
                         long ts, long recvTs) {
        TradeLog log = new TradeLog(src, instrument.asLong(), id, price, volume, cnt, isBuy, ts, recvTs);
        tradePusher.put(StringUtils.join(log.toLongArray(), C.SEPARATOR));
        if(src==Source.Huobi && instrument.asLong() == ASCII.packLong("BTCUSDT")) {
            hbTradeSpan.add(new TradeLog(src, instrument.asLong(), id, price, volume, cnt, isBuy, ts, recvTs));
        }

    }

    @Override
    public void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk,
                          long lastUpdate, long lastReceived) {
    }

}
