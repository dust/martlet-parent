package com.kmfrog.martlet.feed.domain;

import java.util.Objects;

import com.kmfrog.martlet.feed.Source;

public class TradeLog implements Comparable<TradeLog>, InstrumentTimeSpan {
    private Source src;
    private long instrument;
    private long id;
    private long price;
    private long volume;
    private long cnt;
    private boolean isBuy;
    private long ts;
    private long recvTs;


    public TradeLog(Source src, long instrument, long id, long price, long volume, long cnt, boolean isBuy,
                    long ts, long recvTs) {
        this.src = src;
        this.instrument = instrument;
        this.id = id;
        this.price = price;
        this.volume = volume;
        this.cnt = cnt;
        this.isBuy = isBuy;
        this.ts = ts;
        this.recvTs = recvTs;
    }

    public TradeLog(long[] fields) {

        src = Source.values()[(int) fields[0]];
        instrument = fields[1];
        id = fields[2];
        price = fields[3];
        volume = fields[4];
        cnt = fields[5];
        isBuy = fields[6] == 1L;
        ts = fields[7];
        recvTs = fields[8];
    }
    
    public Source getSource() {
        return src;
    }

    public long[] toLongArray(){
        return new long[]{src.ordinal(), instrument, id, price, volume, cnt, isBuy?1:0, ts, recvTs};
    }

    @Override
    public long getTimestamp() {
        return ts;
    }

    @Override
    public long getPrice() {
        return price;
    }
    
    @Override
    public long getVolume() {
        return volume;
    }
    
    @Override
    public long getInstrument() {
        return instrument;
    }

    @Override
    public int compareTo(TradeLog o) {
        if (o == null) {
            return 1;
        }
        long diff = ts - o.ts;
        if (diff != 0) {
            return diff > 0 ? 1 : -1;
        }
        return (int) (recvTs - o.recvTs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TradeLog tradeLog = (TradeLog) o;
        return instrument == tradeLog.instrument &&
                id == tradeLog.id &&
                price == tradeLog.price &&
                volume == tradeLog.volume &&
                cnt == tradeLog.cnt &&
                isBuy == tradeLog.isBuy &&
                ts == tradeLog.ts &&
                recvTs == tradeLog.recvTs &&
                src == tradeLog.src;
    }

    @Override
    public int hashCode() {
        return Objects.hash(src, instrument, id, price, volume, cnt, isBuy, ts, recvTs);
    }
}
