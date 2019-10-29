package com.kmfrog.martlet.feed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.kmfrog.martlet.book.AggregateOrderBook;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderBook;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.feed.impl.BinanceInstrumentDepth;
import com.kmfrog.martlet.feed.impl.BinanceWebSocketHandler;
import com.kmfrog.martlet.feed.impl.HuobiInstrumentDepth;
import com.kmfrog.martlet.feed.impl.HuobiWebSocketHandler;
import com.kmfrog.martlet.feed.impl.OkexInstrumentDepth;
import com.kmfrog.martlet.feed.impl.OkexWebSocketHandler;
import com.kmfrog.martlet.feed.net.FeedBroadcast;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;

/**
 * Hello world!
 *
 */
public class App implements Controller {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    /**
     * 所有交易对的聚合订单表。
     */
    private final Long2ObjectArrayMap<AggregateOrderBook> books;
    /**
     * 来源:单一订单簿(k:v)的集合。方便从来源检索单一订单簿。
     */
    private final Map<Source, Long2ObjectArrayMap<IOrderBook>> multiSrcBooks;
    private static ExecutorService executor = Executors
            .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("MartletAppExecutor-%d").build());
    /**
     * 聚合工作线程。{instrument.asLong : worker}
     */
    private final Map<Long, InstrumentAggregation> aggWorkers;
    /**
     * websocket集合。来源为key.
     */
    private final Map<Source, WebSocketDaemon> websocketDaemons;
    
    private final FeedBroadcast broadcast;

    public App() {
        books = new Long2ObjectArrayMap<>();
        broadcast = new FeedBroadcast("localhost", 5188, 1);

        multiSrcBooks = new ConcurrentHashMap<>();
        websocketDaemons = new ConcurrentHashMap<>();
        aggWorkers = new ConcurrentHashMap<>();
    }

    IOrderBook makesureOrderBook(Source src, long instrument) {
        Long2ObjectArrayMap<IOrderBook> srcBooks = multiSrcBooks.computeIfAbsent(src, (key) -> {
            Long2ObjectArrayMap<IOrderBook> sameSrcBooks = new Long2ObjectArrayMap<>();
            sameSrcBooks.put(instrument, new OrderBook(instrument));
            return sameSrcBooks;
        });
        return srcBooks.computeIfAbsent(instrument, (key) -> new OrderBook(key));
    }

    AggregateOrderBook makesureAggregateOrderBook(Instrument instrument) {
        AggregateOrderBook book = books.computeIfAbsent(instrument.asLong(), (key) -> new AggregateOrderBook(key));
        if (!aggWorkers.containsKey(instrument.asLong())) {
            InstrumentAggregation worker = new InstrumentAggregation(instrument, book, broadcast, this);
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
        websocketDaemons.put(source, wsDaemon);
        wsDaemon.keepAlive();
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        App app = new App();
        Instrument bnbbtc = new Instrument("BTCUSDT", 8, 8);
        app.makesureAggregateOrderBook(bnbbtc);
        IOrderBook btcBook = app.makesureOrderBook(Source.Binance, bnbbtc.asLong());
        //
        BinanceInstrumentDepth btc = new BinanceInstrumentDepth(bnbbtc, btcBook, Source.Binance, app);
        //// BinanceInstrumentDepth eth = new BinanceInstrumentDepth(bnbeth, bnbethBook, Source.Binance, app);
        app.startSnapshotTask(String.format("https://www.binance.com/api/v1/depth?symbol=%s&limit=10", "BTCUSDT"), btc);
        //// app.startSnapshotTask("BNBETH", eth);
        BaseWebSocketHandler handler = new BinanceWebSocketHandler(
                "wss://stream.binance.com:9443/stream?streams=%s@depth", new String[] { "btcusdt" },
                new BinanceInstrumentDepth[] { btc });
        app.startWebSocket(Source.Binance, handler);

        // Instrument btcusdt = new Instrument("BTCUSDT", 8, 8);
        // AggregateOrderBook btcBook = app.makesureOrderBook(btcusdt.asLong());
        //
        IOrderBook hbBtcUsdt = app.makesureOrderBook(Source.Huobi, bnbbtc.asLong());
        HuobiInstrumentDepth hbBtc = new HuobiInstrumentDepth(bnbbtc, hbBtcUsdt, Source.Huobi, app);
        BaseWebSocketHandler hbHandler = new HuobiWebSocketHandler(new String[] { "btcusdt" },
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
        OkexWebSocketHandler okexHandler = new OkexWebSocketHandler(new String[] { "BTC-USDT" },
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

            app.websocketDaemons.get(Source.Okex).keepAlive();
            // okexBtcUsdt.dump(Side.BUY, System.out);
            okexHandler.dumpStats(System.out);
            // now = System.currentTimeMillis();
            // System.out.format("\nOK: %d|%d\n", now - okexBtcUsdt.getLastReceivedTs(), okexBtcUsdt.getLastReceivedTs()
            // - okexBtcUsdt.getLastUpdateTs());
            System.out.println("\n====\n");

            // xieBook.dump(Side.BUY, System.out);
            // app.websocketDaemons.get(Source.Bhex).keepAlive();

            // hbBtcUsdt.dump(Side.BUY, System.out);
            hbHandler.dumpStats(System.out);
            app.websocketDaemons.get(Source.Huobi).keepAlive();
            // now = System.currentTimeMillis();
            // System.out.format("\nHB: %d|%d\n", now - hbBtcUsdt.getLastReceivedTs(), hbBtcUsdt.getLastReceivedTs() -
            // hbBtcUsdt.getLastUpdateTs());
            System.out.println("\n====\n");
            

            // executor.submit(new AggregateRunnable(app.makesureAggregateOrderBook(bnbbtc.asLong()), new Source[]
            // {Source.Binance, Source.Okex, Source.Huobi}, app));
            if (app.aggWorkers.containsKey(bnbbtc.asLong())) {
                app.aggWorkers.get(bnbbtc.asLong()).dumpStats(System.out);
            }
            System.out.println("\n====\n");
            AggregateOrderBook aggBook = app.makesureAggregateOrderBook(bnbbtc);
            System.out.format("%d|%d, %d|%d, %d|%d, %d|%d\n\n", btcBook.getBestBidPrice(), btcBook.getBestAskPrice(),
                    hbBtcUsdt.getBestBidPrice(), hbBtcUsdt.getBestAskPrice(), okexBtcUsdt.getBestBidPrice(),
                    okexBtcUsdt.getBestAskPrice(), aggBook.getBestBidPrice(), aggBook.getBestAskPrice());
            System.out.format("\n\n%s\n", aggBook.dumpPlainText(Side.BUY, 8, 8, 5));
        }
    }

    public void reset(Source mkt, Instrument instrument, BaseInstrumentDepth depth, boolean isSubscribe,
            boolean isConnect) {
        // this.startSnapshotTask(instrument.asString().toUpperCase(), depth);
        websocketDaemons.get(mkt).reset(instrument, depth, isSubscribe, isConnect);
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
    public void onDeviate(Source source, Instrument instrument, IOrderBook book, long bestBid, long bestAsk,
            long lastUpdate, long lastReceived) {
    }

}
