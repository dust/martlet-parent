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
    
    static final long EPOCH_MILLIS = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli();
    
    static final char SEPARATOR = ',';
    static final char SECOND_SEPARATOR = ';';
    static final char THIRD_SEPARATOR = '|';
    


}
