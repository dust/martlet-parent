package com.kmfrog.martlet.maker.exec;

import java.util.Set;

import org.slf4j.Logger;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.maker.service.DepthService;

public class CancelExec extends Exec{
	
	private final TrackBook trackBook;
	private final Set<Long> orderIds;
	private final DepthService depthService;
	private final boolean isOpenOnly;
	private final Instrument instrument;
	private final Logger logger;
	String api;
	String secret;

	public CancelExec(Instrument instrument, Set<Long> orderIds, boolean isOpenOnly, TrackBook trackBook, DepthService depthService, String api, String secret, Logger logger) {
		super(System.currentTimeMillis());
		this.trackBook = trackBook;
		this.orderIds = orderIds;
		this.depthService = depthService;
		this.isOpenOnly = isOpenOnly;
		this.instrument = instrument;
		this.api = api;
		this.secret = secret;
		this.logger = logger;
		
	}

	@Override
	public void run() {
		for(Long orderId: orderIds) {
			if(trackBook.getOrder(orderId)!=null) {
				int effects = depthService.cancelOpenOrder(orderId, api, secret);
				if (effects == 1) {
	                trackBook.remove(orderId);
	            }
			}
		}
	}

}
