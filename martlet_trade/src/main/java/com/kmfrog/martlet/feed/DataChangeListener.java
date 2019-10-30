package com.kmfrog.martlet.feed;

import javax.sound.midi.Instrument;

import com.kmfrog.martlet.book.IOrderBook;

public interface DataChangeListener {
    
    void onDepth(Instrument instrument, IOrderBook book);
    
    void onTrade(Instrument instrument, Object tradeLog);

}
