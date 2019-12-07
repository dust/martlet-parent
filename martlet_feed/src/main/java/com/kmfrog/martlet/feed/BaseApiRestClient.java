package com.kmfrog.martlet.feed;

public abstract class BaseApiRestClient {
	
	protected final String baseUrl;
	protected final String apiKey;
	protected final String secret;
	
	public BaseApiRestClient(String baseUrl, String apiKey, String secret) {
		this.baseUrl = baseUrl;
		this.apiKey = apiKey;
		this.secret = secret;
	}
	
	public void getDepth(String symbol) {
		
	}

}
