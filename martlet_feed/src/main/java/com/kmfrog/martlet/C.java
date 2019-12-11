package com.kmfrog.martlet;

import java.time.LocalDate;
import java.time.ZoneId;

import com.kmfrog.martlet.feed.Source;

public interface C {

    long[] POWERS_OF_TEN = new long[] { 1L, 10L, 100L, 1000L, 10000L, 100000L, 1000000L, 10000000L, 100000000L,
            1000000000L, 10000000000L, 100000000000L, 1000000000000L, 10000000000000L, 100000000000000L,
            1000000000000000L, 10000000000000000L, 100000000000000000L, 1000000000000000000L, };

    long EPOCH_MILLIS = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

    char SEPARATOR = ',';
    char SECOND_SEPARATOR = ';';
    char THIRD_SEPARATOR = '|';

//    int MAX_LEVEL = 50;

    long SYMBOL_DELAY_MILLIS = 300000L;

    double SYMBOL_PRICE_DIFF = 0.05;

    String PREFER_SOURCE_NAME = "Binance";

//    long SPREAD_LOWLIMIT_MILLESIMAL = 2;

//    long TRADE_AVG_WINDOW_MILLIS = 10000;
    
    long LAST_TRADE_WINDOW_MILLIS = 2 * 60000;

    Source[] DEF_SOURCES = new Source[] { Source.Binance, Source.Huobi, Source.Okex };

//    long MONITOR_SLEEP_MILLIS = 25000;
    
    int SPREAD_PRICE_UNIT_SIMPLE_LIMIT = 5;
    
    int SELL_FIRST_RATIO = 80;

    ///////////////////// configuration /////////////////////
    String ALL_SUPPORTED_SYMBOLS = "${supported.symbols}";
    String BINANCE_SUPPORTED = "${binance.supported:[]}";
    String BIKUN_SUPPORTED = "${bikun.supported:[]}";
    String LOEX_SUPPORTED = "${loex.supported:[]}";
    String TAC_SUPPORTED = "${tac.supported:[]}";
    
    
    String BINANCE_WS_DEPTH = "${binance.ws.depth.url}";
    String BINANCE_WS_TRADE = "${binance.ws.trade.url}";
    String BINANCE_REST_URL = "${binance.rest.depth.url}";

    String HUOBI_WS_URL = "${huobi.ws.url}";
    String HUOBI_DEPTH_FMT = "${huobi.depth.fmt}";
    String HUOBI_TRADE_FMT = "${huobi.trade.fmt}";

    String OKEX_WS_URL = "${okex.ws.url}";
    String OKEX_DEPTH_FMT = "${okex.depth.fmt}";
    String OKEX_DEPTH_JOIN_SEPARATOR = "${okex.depth.join.separator}";
    String OKEX_TRADE_FMT = "${okex.trade.fmt}";
    String OKEX_TRADE_JOIN_SEPARATOR = "${okex.trade.join.separator}";
    
    String BHEX_WS_URL = "${bhex.ws.url}";
    String BHEX_DEPTH_FMT = "${bhex.depth.fmt}";
    
    String BIKUN_WS_URL = "${bikun.ws.url}";
    String BIKUN_DEPTH_FMT = "${bikun.depth.fmt}";
    
    String LOEX_REST_URL = "api.base.url.loex";

    String PUB_DEPTH_HOST = "${pub.depth.host}";
    String PUB_DEPTH_PORT = "${pub.depth.port}";
    String PUB_TRADE_HOST = "${pub.trade.host}";
    String PUB_TRADE_PORT = "${pub.trade.port}";
    String PUB_DEPTH_IO_THREAD_CNT = "${pub.depth.io.thread.cnt}";
    String PUB_TRADE_IO_THREAD_CNT = "${pub.trade.io.thread.cnt}";
    
    String MONITOR_SLEEP_MILLIS = "${monitor.sleep.millis:25000}";
   
    
    // for maker
    String DEPTH_FEED_HOST = "${depth.feed.host}";
    String TRADE_FEED_HOST = "${trade.feed.host}";
    String DEPTH_FEED_PORT = "${depth.feed.port}";
    String TRADE_FEED_PORT = "${trade.feed.port}";
    String DEPTH_FEED_IO_THREAD_CNT = "${depth.feed.io.thread.cnt}";
    String TRADE_FEED_IO_THREAD_CNT = "${trade.feed.io.thread.cnt}";
    
    
   
    String OPEN_ORDER_TRACKER_SLEEP_MILLIS = "${open.order.tracker.sleep.millis:25000}";
    String PREFER_SOURCE = "${prefer.source:Binance}";
    String ALL_SOURCES = "${all.sources:[Binance,Huobi,Okex]}";
    String MAX_MAKE_ORDER_VOL_DIFF = "${max.make.order.volume.diff:0.3}";
    String MAKER_SLEEP_MILLIS = "${maker.sleep.millis:510}";
    String SPREAD_LOWLIMIT_MILLESIMAL = "${min.spread.limit:1}";
    String MAX_DELAY_MILLIS = "${max.delay.millis:10000}";
    String PRICE_DIFF = "${price.diff:0.05}";
    String DEPTH_SIZE_STRATEGY = "${depth.size.strategies:[{\"from\":0,\"to\":4,\"discount\":\"0.5\"}]}";
    String AGGREGATE_DELAY_MILLIS = "${aggregate.delay.millis:5000}";
    String TRADE_AVG_WINDOW_MILLIS = "${trade.avg.window.millis:10000}";
    String MAX_LEVEL = "${max.level:60}";
    String MAKER_SUPPORTED_SYMBOLS = "${supported.symbols}";
    String HUOBI_SUPPORTED = "${huobi.supported}";
    String OKEX_SUPPORTED = "${okex.supported}";
    String SPLIT_TRADE_RATIO = "${splitTradeRatio:1.0}";
    String SPLIT_TRADE_SYMBOLS = "${splitTradeSymbols:[]}";
    String SPLIT_TRADE_MAX_DELAY_MILLIS = "${splitTradeMaxDelayMillis:10000}";
    
    String MAKER_TRADE_USER_ID = "${maker.trade.user.id:6149}";
    String BUY_ROBOT_UID = "${buy.robot.uid:6148}";
    String SELL_ROBOT_UID = "${sell.robot.uid:6148}";

}
