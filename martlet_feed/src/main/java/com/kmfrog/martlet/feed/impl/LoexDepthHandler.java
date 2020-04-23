package com.kmfrog.martlet.feed.impl;

import com.kmfrog.martlet.feed.BaseRestHandler;
import com.kmfrog.martlet.feed.SnapshotDataListener;

public class LoexDepthHandler extends BaseRestHandler{

    public LoexDepthHandler(String depthUrlFmt, String[] symbols, SnapshotDataListener[] listeners) {
        super(depthUrlFmt, symbols, listeners);
    }

}
