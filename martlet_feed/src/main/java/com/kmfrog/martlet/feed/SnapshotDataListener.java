package com.kmfrog.martlet.feed;

import com.alibaba.fastjson.JSONObject;

public interface SnapshotDataListener {
    
    Object onSnapshot(String snap);

}
