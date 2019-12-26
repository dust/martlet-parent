package com.kmfrog.martlet.trade;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.OrderEntry;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.trade.exec.TacCancelExec;
import com.kmfrog.martlet.trade.exec.TacPlaceOrderExec;
import com.kmfrog.martlet.util.FeedUtils;

import io.broker.api.client.BrokerApiRestClient;

public abstract class InstrumentChaser extends Thread implements DataChangeListener{
	
	protected final Logger logger = LoggerFactory.getLogger(InstrumentChaser.class);
    protected final Instrument instrument;
    protected final Source source;
	private AtomicBoolean isQuit = new AtomicBoolean(false);
	private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();
	
    protected final TrackBook trackBook;
    protected final Provider provider;
    protected IOrderBook lastBook;
    
    protected final int minSleepMillis;
    protected final int maxSleepMillis;
    protected final long vMin;
    protected final long vMax;
	
	public InstrumentChaser(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            Param args) {
		this.instrument = instrument;
		this.source = src;
		this.trackBook = trackBook;
		this.provider = provider;
		
        minSleepMillis = args.getMinSleepMillis();
        maxSleepMillis = args.getMaxSleepMillis();
        vMin = args.getMinVolume();
        vMax = args.getMaxVolume();
	}
	
	public void run() {
		while(!isQuit.get()) {
			long sleepMillis = FeedUtils.between(minSleepMillis, maxSleepMillis);
			try {
				
				IOrderBook book = depthQueue.poll(sleepMillis, TimeUnit.MILLISECONDS);
                if (book != null) {
                    if (lastBook != null) {
                        lastBook.destroy();
                        lastBook = null;
                    }
                    lastBook = book;
                }

                if (lastBook == null) {
                    continue;
                }
                chasing(lastBook);
			} catch(Exception ex) {
				logger.warn(ex.getMessage(), ex);
			}
		}
		
	}
	
	public abstract void chasing(IOrderBook lastBook);

	@Override
	public void onDepth(Long instrument, IOrderBook book) {
        try {
            depthQueue.put(book);
        } catch (Exception ex) {
            logger.warn(ex.getMessage(), ex);
        }
	}

	@Override
	public void onTrade(Long instrument, TradeLog tradeLog) {
		// TODO Auto-generated method stub
		
	}
	
    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    public void destroy() {
        depthQueue.clear();
    }

}
