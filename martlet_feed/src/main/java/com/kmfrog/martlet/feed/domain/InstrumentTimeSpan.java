package com.kmfrog.martlet.feed.domain;

public interface InstrumentTimeSpan {

    long getTimestamp();

    long getPrice();

    long getInstrument();
    
    long getVolume();

}
