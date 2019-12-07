package com.kmfrog.martlet.feed;

import com.alibaba.fastjson.JSONObject;

public abstract class BaseApiRestClient {
	
	protected final String baseUrl;
	protected final String apiKey;
	protected final String secret;
	
	public BaseApiRestClient(String baseUrl, String apiKey, String secret) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.secret = secret;
	}
	
	public abstract JSONObject getDepth(String symbol);

}
