package com.kmfrog.martlet.maker;

import static com.kmfrog.martlet.C.POWERS_OF_TEN;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;

public class TestTrackBook {

    @Test
    public void testRange() {

        Instrument btcUsdt = new Instrument("BTCUSDT", 8, 8, 6);
        TrackBook book = new TrackBook(btcUsdt);
        for (int i = 0; i < 10; i++) {
            book.entry(i, Side.BUY, (9351 + i) * POWERS_OF_TEN[8], 30 + i * POWERS_OF_TEN[4]);
        }

        for (int i = 0; i < 10; i++) {
            book.entry(i + 10, Side.SELL, (9371 + i) * POWERS_OF_TEN[8], 30 + i * POWERS_OF_TEN[4]);
        }

        Set<Long> ids = book.getOrdersBetween(Side.BUY, 936900000000L, 934400000000L);
        System.out.println(ids);
    }

    @Test
    public void testDiff() {
        Instrument btcUsdt = new Instrument("BTCUSDT", 8, 8, 6);
        TrackBook book = new TrackBook(btcUsdt);
        for (int i = 0; i < 20; i++) {
            book.entry(i, Side.BUY, (9351 + i) * POWERS_OF_TEN[8], 30 + i * POWERS_OF_TEN[4]);
        }

        Set<Long> ids = book.getOrdersBetween(Side.BUY, 936900000000L, 934400000000L);
        Set<Long> ids2 = new HashSet<Long>();
        ids2.addAll(Arrays.asList(18L, 19L, 20L, 21L, 24L));
        System.out.println(Sets.difference(ids, ids2));
        System.out.println(Sets.difference(ids2, ids));

    }

}
