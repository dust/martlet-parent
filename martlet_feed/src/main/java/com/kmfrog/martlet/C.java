package com.kmfrog.martlet;

import java.time.LocalDate;
import java.time.ZoneId;

public interface C {
    
    long[] POWERS_OF_TEN = new long[] {
            1L,
            10L,
            100L,
            1000L,
            10000L,
            100000L,
            1000000L,
            10000000L,
            100000000L,
            1000000000L,
            10000000000L,
            100000000000L,
            1000000000000L,
            10000000000000L,
            100000000000000L,
            1000000000000000L,
            10000000000000000L,
            100000000000000000L,
            1000000000000000000L,
        };
    
    long EPOCH_MILLIS = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();
    
    char SEPARATOR = ',';
    char SECOND_SEPARATOR = ';';
    char THIRD_SEPARATOR = '|';
    
    int MAX_LEVEL = 5;

    long SYMBOL_DELAY_MILLIS = 10000L;

    double SYMBOL_PRICE_DIFF = 0.05;

    String PREFER_SOURCE_NAME = "Binance";

    long SPREAD_LOWLIMIT_MILLESIMAL = 2;

    long TRADE_AVG_WINDOW_MILLIS = 10;
    
    


}
