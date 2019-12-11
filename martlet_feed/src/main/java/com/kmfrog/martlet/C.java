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

    int MAX_LEVEL = 50;

    long SYMBOL_DELAY_MILLIS = 300000L;

    double SYMBOL_PRICE_DIFF = 0.05;

    String PREFER_SOURCE_NAME = "Binance";

    long SPREAD_LOWLIMIT_MILLESIMAL = 2;

    long TRADE_AVG_WINDOW_MILLIS = 10000;
    
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
   
    

}
