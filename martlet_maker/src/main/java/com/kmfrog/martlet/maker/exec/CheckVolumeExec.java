package com.kmfrog.martlet.maker.exec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.maker.core.Provider;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class CheckVolumeExec extends Exec {

    Provider provider;
    Instrument instrument;
    TrackBook trackBook;
    Logger logger;
    final String api;
    final String secret;

    public CheckVolumeExec(Instrument instrument, Provider provider, TrackBook trackBook, Logger logger) {
        super(System.currentTimeMillis());
        this.instrument = instrument;
        this.provider = provider;
        this.trackBook = trackBook;
        this.logger = logger;
        this.api = provider.getTatmasApiKey();
        this.secret = provider.getTatmasSecretKey();
    }

    @Override
    public void run() {
        SymbolAoWithFeatureAndExtra symbolInfo = provider.getSymbolInfo(instrument);
        Source preferSource = provider.getPreferSource(symbolInfo);
        IOrderBook aggBook = provider.getOrderBook(preferSource, instrument);
        if (aggBook.getAskPrices().isEmpty() || aggBook.getBidPrices().isEmpty()) {
            logger.warn("{} aggregate book is empty: {},{}", instrument.asString(), aggBook.getAskPrices().isEmpty(),
                    aggBook.getBidPrices().isEmpty());
            return;
        }

        Set<Long> shrinkAsks = arrangeOpenOrderByVolume(aggBook, Side.SELL);
        Set<Long> shrinkBids = arrangeOpenOrderByVolume(aggBook, Side.BUY);
        Set<Long> shrinkOrderIds = Sets.union(shrinkAsks, shrinkBids);
        // 应该避免撤单量过大。只撤掉1/3的随机订单。
        if (shrinkOrderIds.size() >= provider.getMaxLevel(symbolInfo) / 3) {
            List<Long> list = new ArrayList<>();
            list.addAll(shrinkOrderIds);
            shrinkOrderIds.clear();
            Collections.shuffle(list);
            shrinkOrderIds.addAll(list.subList(0, list.size() / 3));
        }
        
        if (shrinkOrderIds.size() > 0) {
            provider.submit(
                    new CancelExec(instrument, shrinkOrderIds, false, trackBook, provider.getDepthService(), api, secret, logger));
            if (logger.isInfoEnabled()) {
                logger.info("{} shrink orders: {}", instrument.asString(), shrinkOrderIds);
            }
        }

    }

    private Set<Long> arrangeOpenOrderByVolume(IOrderBook aggBook, Side side) {
        LongSortedSet openPrices = trackBook.getPrices(side);
        Set<Long> shrinkOrderIds = new HashSet<>();
        for (LongBidirectionalIterator iter = openPrices.iterator(); iter.hasNext();) {
            long price = iter.nextLong();
            PriceLevel pl = trackBook.getPriceLevel(side, price);
            long bookVolume = side == Side.SELL ? aggBook.getAskSize(price) : aggBook.getBidSize(price);
            if (pl != null && bookVolume > 0) {
                long diff = pl.getSize() - bookVolume;
                if (Math.abs(diff) / (bookVolume * 1.0) > provider.getMaxVolumeDiff(instrument)) {
                    if (diff > 0 && pl.getCount() > 0) {
                        shrinkOrderIds.addAll(pl.shrinkOrders(bookVolume));
                    } else {
                        // 开放订单数量小于订单簿数量的30%，把当前订单撤单。等待深度和摆盘线程处理。（会使用新数量重新下单）。
                        shrinkOrderIds.addAll(pl.getOrderIds());
                    }
                }
            }
        }
        return shrinkOrderIds;
    }

}
