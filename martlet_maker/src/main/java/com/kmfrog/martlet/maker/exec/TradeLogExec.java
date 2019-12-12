package com.kmfrog.martlet.maker.exec;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;

import com.ctrip.framework.foundation.internals.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.kmfrog.martlet.book.IOrderBook;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.book.Side;
import com.kmfrog.martlet.book.TrackBook;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.SymbolAoWithFeatureAndExtra;
import com.kmfrog.martlet.feed.domain.TradeLog;
import com.kmfrog.martlet.maker.core.Provider;
import com.kmfrog.martlet.maker.model.entity.Order;
import com.kmfrog.martlet.maker.model.entity.Trade;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;
import com.kmfrog.martlet.util.Fmt;

public class TradeLogExec extends Exec {

    TradeService tradeService;
    DepthService depthService;
    TradeLog log;
    TradeLog lastLog;
    Source src;
    Instrument instrument;
    Provider provider;
    Logger logger;

    public TradeLogExec(TradeLog log, Source src, Instrument instrument, Provider provider, Logger logger) {
        super(System.currentTimeMillis());
        this.log = log;
        this.src = src;
        this.instrument = instrument;
        this.provider = provider;
        this.logger = logger;
        this.tradeService = provider.getTradeService();
        this.depthService = provider.getDepthService();

    }

    @Override
    public void run() {
        try {
            // 为什么用来源方的order book？因为聚合的order book约束更松，和实际偏离更大。
            IOrderBook book = provider.getOrderBook(src, instrument);
            long bestOffer = book.getBestAskPrice();
            long bestBid = book.getBestBidPrice();
            long tradePrice = log.getPrice();
            // if (logger.isInfoEnabled()) {
            // logger.info("{} {} bbo:{},{} trade:{}", src, instrument.asString(), bestBid, bestOffer, tradePrice);
            // }
            if (tradePrice < bestBid || tradePrice > bestOffer) {
                if(logger.isInfoEnabled()) {
                    logger.info("{} {} trade log out of book: {},{},{}", src, instrument.asString(), bestBid, bestOffer, tradePrice);
                }
                return;
            }

            TrackBook trackBook = provider.getTrackBook(Source.Bitrue, instrument);
            long openBid1 = trackBook.getBestLevel(Side.BUY) != null ? trackBook.getBestLevel(Side.BUY).getPrice() : 0L;
            long openAsk1 = trackBook.getBestLevel(Side.SELL) != null ? trackBook.getBestLevel(Side.SELL).getPrice()
                    : 0L;
            if (tradePrice < openBid1 || tradePrice > openAsk1) {
                if(logger.isInfoEnabled()) {
                    logger.info("{} {} trade log out of open order: {},{},{}", src, instrument.asString(), openBid1, openAsk1, tradePrice);
                }
                return;
            }

            SymbolAoWithFeatureAndExtra symbolInfo = provider.getSymbolInfo(instrument);
            BigDecimal originPrice = Fmt.dec(tradePrice, instrument.getPriceFractionDigits());
            BigDecimal originVolume = Fmt.dec(log.getVolume(), instrument.getSizeFractionDigits());
            Optional<BigDecimal> fixedVolumeOpt = getFixedVolume(symbolInfo, originVolume, src, logger);
            Optional<BigDecimal> fixedPriceOpt = getFixedPrice(instrument, symbolInfo, originPrice, logger);
            // System.out.println(src+"|"+instrument.asString()+"|"+fixedVolumeOpt.isPresent()+"|"+fixedPriceOpt.isPresent()+"|"+log.toString());
            if (fixedVolumeOpt.isPresent() && fixedPriceOpt.isPresent()) {
                BigDecimal newVolume = fixedVolumeOpt.get();
                BigDecimal newPrice = fixedPriceOpt.get();

                
                String symbolName = instrument.asString().toLowerCase();
//                Pair<BigDecimal, BigDecimal> priceRange = depthService.getPriceRange(symbolName);
//                BigDecimal bid1 = priceRange.getLeft();
//                BigDecimal ask1 = priceRange.getRight();

//                if (newPrice.compareTo(bid1) >= 0 && newPrice.compareTo(ask1) <= 0) {
                    if (provider.getSplitTradeSymbols().contains(symbolName)
                            && System.currentTimeMillis() - log.getTimestamp() < provider
                                    .getSplitTradeMaxDelayMillis(symbolInfo)
                            && Math.random() < provider.getSplitTradeRatio(symbolInfo)) {
                        List<Order> splitOrders = new ArrayList<>();
                        splitOrders.add(Order.buildOrderByPriceLevel(instrument.asString(), Side.BUY, newPrice, newVolume, provider.getMakerTradeUserId(symbolInfo)));
                        splitOrders.add(Order.buildOrderByPriceLevel(instrument.asString(), Side.SELL, newPrice, newVolume, provider.getMakerTradeUserId(symbolInfo)));
                        provider.submit(new PlaceOrderExec(instrument, splitOrders, provider.getDepthService(),
                                tradeService, trackBook, logger));
                        if(logger.isInfoEnabled()) {
                            logger.info("{} {} trade split to depth: {}@{}", src, instrument.asString(), newVolume, newPrice);
                        }
                        return;
                    }
                    
//                    Trade trade = buildTrade(newPrice, newVolume, log.isBuy(), log.getTimestamp(), symbolInfo);
//                    tradeService.saveTrade(trade);
                }
//            }

        } catch (Exception ex) {
            logger.warn("{} {} trade exec {}", src, instrument.asString(), ex.getMessage());
        }
    }

    

    private BigDecimal getVolumeDiscount(SymbolAoWithFeatureAndExtra symbolInfo, Source src) {
//        JsonNode cfg = symbolInfo.getExtra();
//        if (cfg.has("lokiConfig") && cfg.get("lokiConfig").has("sourceDiscount")) {
//            JsonNode sourceDiscountCfg = cfg.get("lokiConfig").get("sourceDiscount");
//            String discountTxt = sourceDiscountCfg.path(src.name().toLowerCase()).asText();
//            if (StringUtils.isNotBlank(discountTxt)) {
//                return new BigDecimal(discountTxt);
//            }
//        }
//        String volumeDiscountText = symbolInfo.getExtra().path(TRADE_VOLUME_DISCOUNT_KEY).asText();
//        BigDecimal volumeDiscount;
//        if (StringUtils.isNotBlank(volumeDiscountText)) {
//            volumeDiscount = new BigDecimal(volumeDiscountText);
//        } else {
//            volumeDiscount = TRADE_VOLUME_DISCOUNT;
//        }
//        return volumeDiscount;
        return provider.getTradeVolumeFactor(symbolInfo);
    }

    private Optional<BigDecimal> getFixedVolume(SymbolAoWithFeatureAndExtra symbolInfo,
            BigDecimal originalVolume, Source src, Logger logger) {
        BigDecimal volumeDiscount = getVolumeDiscount(symbolInfo, src);
        // 按比例缩小+精度处理后,判断是否满足最小交易量
        BigDecimal fixedVolume = originalVolume.multiply(volumeDiscount)
                .multiply(new BigDecimal(new Random().nextDouble() * 2))
                .setScale(8, BigDecimal.ROUND_DOWN);
        if (fixedVolume.compareTo(BigDecimal.valueOf(0.0001)) >= 0) {
            return Optional.of(fixedVolume);
        }
        logger.warn("Trade for symbol {} has invalid volume {}", instrument.asString(), fixedVolume);
        return Optional.empty();
    }

    private static Optional<BigDecimal> getFixedPrice(Instrument inst, SymbolAoWithFeatureAndExtra symbolInfo, BigDecimal originalPrice,
            Logger logger) {
        BigDecimal fixedPrice;
        Optional<BigDecimal> priceDiscountOpt = getPriceStrategy(symbolInfo);
        if (priceDiscountOpt.isPresent()) {
            fixedPrice = priceDiscountOpt.get().multiply(originalPrice);
        } else {
            fixedPrice = originalPrice;
        }
        fixedPrice = fixedPrice.setScale(inst.getShowPriceFractionDigits(), BigDecimal.ROUND_DOWN);
        if (fixedPrice.compareTo(BigDecimal.ZERO) > 0) {
            return Optional.of(fixedPrice);
        }
        logger.warn("Trade for symbol {} has invalid price {}", inst.asString(), fixedPrice);
        return Optional.empty();
    }

    public static Optional<BigDecimal> getPriceStrategy(SymbolAoWithFeatureAndExtra symbolInfo) {
        BigDecimal priceDiscount = null;
//        String discountStr = symbolInfo.getExtra().path(PRICE_DISCOUNT_KEY).asText();
//        if (StringUtils.isNotBlank(discountStr)) {
//            priceDiscount = new BigDecimal(discountStr);
//        }
        return Optional.ofNullable(priceDiscount);
    }
}
