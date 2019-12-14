package com.kmfrog.martlet.trade;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.DataChangeListener;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.util.FeedUtils;
import com.kmfrog.martlet.util.Fmt;

/**
 * 以刷量为目的的对敲交易
 * 
 * @author dust Nov 15, 2019
 *
 */
public abstract class InstrumentSoloDunk extends Thread implements DataChangeListener {

    protected final Logger logger = LoggerFactory.getLogger(InstrumentSoloDunk.class);
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

    public InstrumentSoloDunk(Instrument instrument, Source src, TrackBook trackBook, Provider provider,
            Param args) {
        source = src;
        this.instrument = instrument;
        this.trackBook = trackBook;
        this.provider = provider;

        minSleepMillis = args.getMinSleepMillis();
        maxSleepMillis = args.getMaxSleepMillis();
        vMin = args.getMinVolume();
        vMax = args.getMaxVolume();
    }

    public void run() {

        while (!isQuit.get()) {
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

                long bid1 = lastBook.getBestBidPrice();
                long ask1 = lastBook.getBestAskPrice();
                long price = FeedUtils.between(bid1, ask1, 0.5);
                System.out.println(getClass().toString() + bid1 + "|" + ask1 + "|" + price);
                String bidStr = Fmt.fmtNum(bid1, instrument.getPriceFractionDigits(), instrument.getShowPriceFractionDigits());
                String askStr = Fmt.fmtNum(ask1, instrument.getPriceFractionDigits(), instrument.getShowPriceFractionDigits());
                String priceStr = Fmt.fmtNum(price, instrument.getPriceFractionDigits(), instrument.getShowPriceFractionDigits());
                System.out.println(getClass().toString() + bidStr + "|" + askStr + "|" + priceStr);
                if(!priceStr.equals(bidStr) && !priceStr.equals(askStr)) {
                	placeHedgeOrder(price, (ask1 - bid1) / instrument.getPriceFactor(), lastBook);
                }
//                if(price != bid1 && price != ask1) {
//                	placeHedgeOrder(price, (ask1 - bid1) / instrument.getPriceFactor(), lastBook);
//                }
            } catch (InterruptedException ex) {
                logger.warn(ex.getMessage(), ex);
            } catch (Exception ex) {
                logger.warn(ex.getMessage(), ex);
            }
        }
    }

    public abstract void placeHedgeOrder(long price, long spreadSize, IOrderBook book);

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

    }

    public void quit() {
        isQuit.compareAndSet(false, true);
        interrupt();
    }

    public void destroy() {
        depthQueue.clear();
    }
}
