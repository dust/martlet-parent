package com.kmfrog.martlet.feed;

import javax.sound.midi.Instrument;

public interface DataChangeListener {
    
    void onDepth(Instrument instrument/*, IOrderBook book*/);

}
