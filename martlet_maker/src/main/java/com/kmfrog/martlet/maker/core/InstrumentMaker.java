package com.kmfrog.martlet.maker.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.feed.domain.VolumeStrategy;
import com.kmfrog.martlet.maker.exec.CancelExec;
import com.kmfrog.martlet.maker.exec.PlaceOrderExec;
import com.kmfrog.martlet.maker.feed.DataChangeListener;
import com.kmfrog.martlet.maker.model.entity.Order;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;
import com.kmfrog.martlet.util.Fmt;

import it.unimi.dsi.fastutil.longs.Long2LongArrayMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class InstrumentMaker extends Thread implements DataChangeListener {

    final Logger logger = LoggerFactory.getLogger(InstrumentMaker.class);
    private final Instrument instrument;
    // private final SymbolAoWithFeatureAndExtra symbolInfo;
    private AtomicBoolean isQuit = new AtomicBoolean(false);
    private BlockingQueue<IOrderBook> depthQueue = new PriorityBlockingQueue<>();

    private final TrackBook trackBook;
    private final Provider provider;
    private final AtomicLong lastAct = new AtomicLong(0L);

    public InstrumentMaker(Instrument instrument, TrackBook trackBook, Provider provider) {
        super(String.format("%s-%s-%d", InstrumentMaker.class.getSimpleName(), instrument.asString(),
                instrument.asLong()));
        this.instrument = instrument;
        // this.symbolInfo = symbolInfo;
        this.trackBook = trackBook;
        this.provider = provider;
    }

    public void run() {

        while (!isQuit.get()) {
            try {
                IOrderBook book = depthQueue.take();
                Source src = Source.values()[(int) book.getSourceValue()];
                provider.setOrderBook(src, instrument, book);

                SymbolAoWithFeatureAndExtra symbolInfo = provider.getSymbolInfo(instrument);
                long[] bbo = getBBO(symbolInfo);
                Source preferSource = provider.getPreferSource(symbolInfo);
                if ((preferSource != null && src != preferSource) || bbo == null || bbo[0] == 0L || bbo[1] == 0L) {
                    if (src == preferSource) {
                        logger.info("bbo is empty! {} {} {}", src, instrument.asString(),
                                bbo == null ? "null" : "not null");
                    }
                    continue;
                }
                if (provider.getMakerSleepMillis(symbolInfo) > 0
                        && System.currentTimeMillis() - lastAct.get() < provider.getMakerSleepMillis(symbolInfo)) {
                    continue;
                }
                lastAct.set(System.currentTimeMillis());
                // System.out.format("%s - %d - %d\n", getClass(), lastAct.get(), System.currentTimeMillis());

                // 得到最糟糕的买单。>bbo[0](${bid})，逆序：从大到小。
                Set<Long> worstBidOrders = trackBook.getWorseOrders(Side.BUY, bbo[0]);
                // 得到最糟糕的卖单。<bbo[1](${ask})，顺序：从小到大。
                Set<Long> worstAskOrders = trackBook.getWorseOrders(Side.SELL, bbo[1]);
                // 立即撤单
                Set<Long> allCancelIds = Sets.union(worstBidOrders, worstAskOrders);
                DepthService depthService = provider.getDepthService();
                if (allCancelIds.size() > 0) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("{} {} cancel worst bids: {}", src, instrument.asString(),
                                allCancelIds.toString());
                    }
                    // 比较危险的订单，状态为0的订单也须要撤掉。
                    provider.submit(new CancelExec(instrument, allCancelIds, false, trackBook, depthService, logger));
                }

//                IOrderBook srcBook = src == Source.Bitrue ? book : provider.getAggBook(instrument);
                mirrorDepthFromSrcBook(src, bbo[0], bbo[1], book, depthService, symbolInfo);

            } catch (InterruptedException ex) {
                logger.warn("{} Interrupted.{}", instrument.asString(), ex.getMessage());
                ex.printStackTrace();
            } catch (NullPointerException ex) {
                logger.warn("{}: NullPointerException", instrument.asString(), ex.getMessage());
                ex.printStackTrace();
            } catch (Exception ex) {
                logger.warn("{} exchange.{}", instrument.asString(), ex.getMessage());
                ex.printStackTrace();
            }

        }

    }

    private void mirrorDepthFromSrcBook(Source src, long bestBid, long bestOffer, IOrderBook mixBook,
            DepthService depthService, SymbolAoWithFeatureAndExtra symbolInfo) {
        if (mixBook.getAskPrices().isEmpty() || mixBook.getBidPrices().isEmpty()) {
            // 一般出现在刚启动。
            logger.warn("{} bbo:{},{}, mixBook is empty:{}, {}", src, bestBid, bestOffer,
                    mixBook.getAskPrices().isEmpty(), mixBook.getBidPrices().isEmpty());
            return;
        }
        // 从买一开始获得价格列表。
        LongSortedSet bids = mixBook.getBidPrices().tailSet(bestBid);
        // 从卖一开始获得价格列表。
        LongSortedSet asks = mixBook.getAskPrices().tailSet(bestOffer);
        LongSortedSet openBids = trackBook.getPrices(Side.BUY);
        LongSortedSet openAsks = trackBook.getPrices(Side.SELL);

        Set<Long> addBids = Sets.difference(bids, openBids);
        Set<Long> addAsks = Sets.difference(asks, openAsks);
        Set<Long> rmBids = Sets.difference(openBids, bids);
        Set<Long> rmAsks = Sets.difference(openAsks, asks);

        // 需要排除的状态。初始状态订单不撤。
        int initStatus = 0;
        Set<Long> cancelBidOrders = new HashSet<>();
        Set<Long> cancelAskOrders = new HashSet<>();
        rmBids.forEach(k -> cancelBidOrders.addAll(trackBook.getPriceLevel(Side.BUY, k).getOrderIds(initStatus)));
        rmAsks.forEach(k -> cancelAskOrders.addAll(trackBook.getPriceLevel(Side.SELL, k).getOrderIds(initStatus)));
        SetView<Long> all = Sets.union(cancelBidOrders, cancelAskOrders);
        if (all.size() > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} {} cancel open orders : {}", src, instrument.asString(), all.toString());
            }
            provider.submit(new CancelExec(instrument, all, false, trackBook, depthService, logger));
        }

        List<Order> allOrders = buildOrder(Side.BUY, addBids, mixBook, symbolInfo);
        List<Order> addAskOrders = buildOrder(Side.SELL, addAsks, mixBook, symbolInfo);
        allOrders.addAll(addAskOrders);
        if (allOrders.size() > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} {} place orders : {}", src, instrument.asString(), allOrders.toString());
            }
            TradeService tradeService = provider.getTradeService();
            provider.submit(new PlaceOrderExec(instrument, allOrders, depthService, tradeService, trackBook, logger));
        }

        // 删除order mixBook以外的全部订单
        long lastAsk = mixBook.getAskPrices().lastLong();
        long lastBid = mixBook.getBidPrices().lastLong();
        Set<Long> outOfBookAsks = trackBook.getBetterOrders(Side.SELL, lastAsk, initStatus);
        Set<Long> outOfBookBids = trackBook.getBetterOrders(Side.BUY, lastBid, initStatus);
        Set<Long> outOfBookOrderIds = Sets.union(outOfBookAsks, outOfBookBids);
        // TODO: 如果撤单太快太多，可以在此考虑单边，限量（排序）撤单。
        if (outOfBookOrderIds.size() > 0) {
            if (logger.isDebugEnabled()) {
                logger.debug("{} {} cancel out of book : {}", src, instrument.asString(), outOfBookOrderIds.toString());
            }
            provider.submit(new CancelExec(instrument, outOfBookOrderIds, false, trackBook, depthService, logger));
        }
    }

    private VolumeStrategy getVolumeStrategy(List<VolumeStrategy> strategies, int index) {
        for (VolumeStrategy vs : strategies) {
            if (index >= vs.getFrom() && index < vs.getTo()) {
                return vs;
            }
        }
        return null;
    }

    List<Order> buildOrder(Side side, Set<Long> prices, IOrderBook book, SymbolAoWithFeatureAndExtra symbolInfo) {
        List<Order> ret = new ArrayList<>();
        if (prices.isEmpty()) {
            return ret;
        }

        int volScale = 8; //symbolInfo.getShowVolumeScale();
        List<VolumeStrategy> volumeStrategies = provider.getVolumeStrategy(symbolInfo);
        TreeSet<Long> sortedSet = new TreeSet<>(prices);

        if (side == Side.BUY) {
            int index = 0;
            for (Iterator<Long> iter = sortedSet.descendingIterator(); iter.hasNext();) {
                Long priceObj = iter.next();
                long volume = book.getBidSize(priceObj.longValue());
                BigDecimal decPrice = Fmt.dec(priceObj.longValue(), instrument.getPriceFractionDigits());
                BigDecimal decVolume;
                VolumeStrategy volumeStrategy = getVolumeStrategy(volumeStrategies, index);
                if (volumeStrategy != null) {
                    decVolume = Fmt.dec(volume, instrument.getSizeFractionDigits())
                            .multiply(new BigDecimal(volumeStrategy.getDiscount()))
                            .setScale(volScale, BigDecimal.ROUND_HALF_DOWN);
                } else {
                    decVolume = Fmt.dec(volume, instrument.getSizeFractionDigits()).setScale(volScale,
                            BigDecimal.ROUND_HALF_DOWN);
                }
                ret.add(Order.buildOrderByPriceLevel(instrument.asString(), Side.BUY, decPrice, decVolume, provider.getBuyRobotId(symbolInfo)));
                index++;
            }

        } else if (side == Side.SELL) {
            int index = 0;
            for (Iterator<Long> iter = sortedSet.iterator(); iter.hasNext();) {
                Long price = iter.next();
                long volume = book.getAskSize(price.longValue());
                BigDecimal decPrice = Fmt.dec(price.longValue(), instrument.getPriceFractionDigits());
                BigDecimal decVolume;
                VolumeStrategy volumeStrategy = getVolumeStrategy(volumeStrategies, index);
                if (volumeStrategy != null) {
                    decVolume = Fmt.dec(volume, instrument.getSizeFractionDigits())
                            .multiply(new BigDecimal(volumeStrategy.getDiscount()))
                            .setScale(volScale, BigDecimal.ROUND_HALF_DOWN);
                } else {
                    decVolume = Fmt.dec(volume, instrument.getSizeFractionDigits()).setScale(volScale,
                            BigDecimal.ROUND_HALF_DOWN);
                }
                ret.add(Order.buildOrderByPriceLevel(instrument.asString(), Side.SELL, decPrice, decVolume, provider.getBuyRobotId(symbolInfo)));
                index++;
            }
        }
        return ret;
    }

    /**
     * 获得最优出价和供应。（Best Bid and Offer),
     * 
     * @return [${bid}, ${offer}]
     */
    public long[] getBBO(SymbolAoWithFeatureAndExtra symbolInfo) {
        long floatDownward = 10000 - provider.getSpreadLowLimitMillesimal(symbolInfo) / 2;
        long floatUpward = 10000 + provider.getSpreadLowLimitMillesimal(symbolInfo) / 2;
        Source preferSource = provider.getPreferSource(symbolInfo);
        if (preferSource != null) {
            IOrderBook book = provider.getOrderBook(preferSource, instrument);
            if (book == null) {
                return null;
            }

            return new long[] { book.getBestBidPrice() * floatDownward / 10000,
                    book.getBestAskPrice() * floatUpward / 10000 };
        }

        Long2LongMap bidMap = new Long2LongArrayMap();
        Long2LongMap askMap = new Long2LongArrayMap();
        Long2LongMap tsMap = new Long2LongArrayMap();

        Source[] defSources = provider.getAllSource();
        for (Source src : defSources) {
            IOrderBook book = provider.getOrderBook(src, instrument);
            bidMap.put(src.ordinal(), book.getBestBidPrice());
            askMap.put(src.ordinal(), book.getBestAskPrice());
            tsMap.put(src.ordinal(), book.getLastUpdateTs());
        }

        long[] tsArray = tsMap.values().toLongArray();
        LongSummaryStatistics tsStats = LongStream.of(tsArray).summaryStatistics();
        long min = tsStats.getMin();
        long max = tsStats.getMax();
        long maxDelayMillis = provider.getMaxDelayMillis(symbolInfo);
        if (max - min > maxDelayMillis) {
            long src = findSourceValue(tsMap, min, defSources);
            if (src > Source.Mix.ordinal()) { // Source.Mix == 0
                bidMap.remove(src);
                askMap.remove(src);
            }
        }

        long[] bidArray = bidMap.values().toLongArray();
        LongSummaryStatistics bidStats = LongStream.of(bidArray).summaryStatistics();
        long avg = bidStats.getSum() / bidStats.getCount();
        min = bidStats.getMin();
        max = bidStats.getMax();
        double maxPriceDiff = provider.getMaxPriceDiff(symbolInfo);
        if ((max - min) / (avg * 1.0) > maxPriceDiff) {
            long diffSrc = findSourceValue(bidMap, max - avg > avg - min ? max : min, defSources);
            if (diffSrc > Source.Mix.ordinal()) {
                bidMap.remove(diffSrc);
                askMap.remove(diffSrc);
            }
        }

        long[] askArray = askMap.values().toLongArray();
        LongSummaryStatistics askStats = LongStream.of(askArray).summaryStatistics();
        avg = askStats.getSum() / askStats.getCount();
        min = askStats.getMin();
        max = askStats.getMax();
        if ((max - min) / (avg * 1.0) > maxPriceDiff) {
            long diffSrc = findSourceValue(askMap, max - avg > avg - min ? max : min, defSources);
            if (diffSrc > Source.Mix.ordinal()) {
                bidMap.remove(diffSrc);
                askMap.remove(diffSrc);
            }
        }

        askArray = askMap.values().toLongArray();
        bidArray = bidMap.values().toLongArray();
        if (askArray.length == 0 || bidArray.length == 0) {
            return null;
        }

        askStats = LongStream.of(askArray).summaryStatistics();
        bidStats = LongStream.of(bidArray).summaryStatistics();

        return new long[] { bidStats.getSum() / bidStats.getCount() * floatDownward / 2000,
                askStats.getSum() / askStats.getCount() * floatUpward / 2000 };

    }

    private static long findSourceValue(Long2LongMap map, long v, Source[] sources) {
        for (Source src : sources) {
            if (map.get(src.ordinal()) == v) {
                return src.ordinal();
            }
        }
        return -1L;
    }

    @Override
    public void onDepth(Long lngInstrument, IOrderBook book) {
        try {
            depthQueue.put(book);
        } catch (Exception ex) {
            logger.warn("{} put order book({}) {}", instrument.asString(), book == null ? -1 : book.getLastReceivedTs(),
                    ex.getMessage());
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
