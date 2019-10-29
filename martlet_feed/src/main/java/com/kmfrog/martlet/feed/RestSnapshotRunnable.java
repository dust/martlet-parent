package com.kmfrog.martlet.feed;

import java.util.Map;

import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 使用REST方式产生初始快照的实现类。它是一个驱动(主动)/线程对象。
 * @author dust Oct 11, 2019
 *
 */
public class RestSnapshotRunnable implements Runnable {

    private final String url;
    private final String method;
    private final Map<String, String> params;
    private final Map<String, String> headers;

    private SnapshotDataListener listener;

    private OkHttpClient client = new OkHttpClient();

    public RestSnapshotRunnable(String url, String method, Map<String, String> headers, Map<String, String> params,
            SnapshotDataListener snapshotDataListener) {
        this.url = url;
        this.method = method.toUpperCase();
        this.params = params;
        this.headers = headers;

        this.listener = snapshotDataListener;
    }

    public void run() {
        String respText = null;
        try {
            if ("GET".equals(method)) {
                Request.Builder reqBuilder = new Request.Builder().url(url);
                if(headers!=null && !headers.isEmpty()) {
                    reqBuilder.headers(Headers.of(headers));
                }
               
                Request request = reqBuilder.build();
                Response response = client.newCall(request).execute();
                respText = response.body().string();
            } else if ("POST".equals(method)) {
                
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        if(respText != null) {
            listener.onSnapshot(respText);
        }

    }

}
