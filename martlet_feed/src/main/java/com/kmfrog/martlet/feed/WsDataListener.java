package com.kmfrog.martlet.feed;

import com.alibaba.fastjson.JSONObject;

/**
 * websocket数据监听器。
 * @author dust Oct 11, 2019
 *
 */
public interface WsDataListener {

    void onMessage(String msg);
    
    void onJSON(JSONObject json, boolean isSnapshot);
    
    void onReset(int errCode, String reason);

}
