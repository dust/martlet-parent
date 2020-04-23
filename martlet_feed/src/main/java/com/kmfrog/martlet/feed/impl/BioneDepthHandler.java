package com.kmfrog.martlet.feed.impl;

import com.kmfrog.martlet.feed.BaseRestHandler;
import com.kmfrog.martlet.feed.SnapshotDataListener;

public class BioneDepthHandler extends BaseRestHandler{

	public BioneDepthHandler(String depthUrlFmt, String[] symbolNames, SnapshotDataListener[] listeners) {
		super(depthUrlFmt, symbolNames, listeners);
	}

}
