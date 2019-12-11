package com.kmfrog.martlet.maker.exec;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.PriceLevel;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.maker.core.Provider;
import com.kmfrog.martlet.maker.model.entity.Order;
import com.kmfrog.martlet.maker.service.DepthService;

import it.unimi.dsi.fastutil.longs.LongSortedSet;

public class CheckTradeOrderExec extends Exec {

    final Source src;
    final Instrument instrument;
    final SymbolAoWithFeatureAndExtra symbolInfo;
    final Provider provider;
    final DepthService depthService;
    final Logger logger;
    // static Logger logger = LoggerFactory.getLogger(CheckTradeOrderExec.class);

    public CheckTradeOrderExec(Source src, Instrument instrument, SymbolAoWithFeatureAndExtra symbolInfo,
            Provider provider, Logger logger) {
        super(System.currentTimeMillis());
        this.src = src;
        this.symbolInfo = symbolInfo;
        this.instrument = instrument;
        this.provider = provider;
        this.logger = logger;
        depthService = provider.getDepthService();
    }

    @Override
    public void run() {
        try {

            Integer tradeUserId = provider.getMakerTradeUserId(symbolInfo);
            List<Order> openBidTradeOrders = depthService.getOpenOrders(instrument.asString(), Side.BUY, tradeUserId);
            List<Order> openAskTradeOrders = depthService.getOpenOrders(instrument.asString(), Side.SELL, tradeUserId);

            TrackBook openTradeBook = new TrackBook(instrument);
            openBidTradeOrders.forEach(ord -> {
                if (ord.getStatus() != 0) {
                    long volume = ord.getVolume().subtract(ord.getDealVolume())
                            .multiply(new BigDecimal(instrument.getSizeFactor())).longValue();
                    long price = ord.getPrice().multiply(new BigDecimal(instrument.getPriceFactor())).longValue();
                    openTradeBook.entry(ord.getId().longValue(), Side.BUY, price, volume, ord.getStatus());
                }
            });
            openAskTradeOrders.forEach(ord -> {
                if (ord.getStatus() != 0) {
                    long price = ord.getPrice().multiply(new BigDecimal(instrument.getPriceFactor())).longValue();
                    long volume = ord.getVolume().subtract(ord.getDealVolume())
                            .multiply(new BigDecimal(instrument.getSizeFactor())).longValue();
                    openTradeBook.entry(ord.getId().longValue(), Side.SELL, price, volume, ord.getStatus());
                }
            });

            Set<Long> rmIds = new HashSet<>();
            LongSortedSet askPrices = openTradeBook.getPrices(Side.SELL);
            LongSortedSet bidPrices = openTradeBook.getPrices(Side.BUY);
            Set<Long> diff1 = Sets.difference(askPrices, bidPrices);
            diff1.forEach(p -> {
                PriceLevel level = openTradeBook.getPriceLevel(Side.SELL, p.longValue());
                if (level != null) {
                    rmIds.addAll(level.getOrderIds());
                }
            });

            Set<Long> diff2 = Sets.difference(bidPrices, askPrices);
            diff2.forEach(p -> {
                PriceLevel level = openTradeBook.getPriceLevel(Side.BUY, p.longValue());
                if (level != null) {
                    rmIds.addAll(level.getOrderIds());
                }
            });

            if (rmIds.size() > 0) {
                if (logger.isInfoEnabled()) {
                    logger.info("cancel SplitTradeOrder bids: {}, ask:{}, orders:{}", diff1.toString(),
                            diff2.toString(), rmIds.toString());
                }
                provider.submit(new CancelExec(instrument, rmIds, false, openTradeBook, depthService, logger));
            }
        } catch (Exception ex) {
            logger.warn("{}-{}-{} {}, {}", src.name(), instrument.asString(), instrument.asLong(),
                    CheckTradeOrderExec.class.getSimpleName(), ex.getMessage());
        }
    }

}
