package com.kmfrog.martlet.maker;

import static com.kmfrog.martlet.C.ALL_SOURCES;
import static com.kmfrog.martlet.C.ALL_SUPPORTED_SYMBOLS;
import static com.kmfrog.martlet.C.BUY_ROBOT_UID;
import static com.kmfrog.martlet.C.DEPTH_FEED_HOST;
import static com.kmfrog.martlet.C.DEPTH_FEED_IO_THREAD_CNT;
import static com.kmfrog.martlet.C.DEPTH_FEED_PORT;
import static com.kmfrog.martlet.C.DEPTH_SIZE_STRATEGY;
import static com.kmfrog.martlet.C.MAKER_SLEEP_MILLIS;
import static com.kmfrog.martlet.C.MAKER_TRADE_USER_ID;
import static com.kmfrog.martlet.C.MAX_DELAY_MILLIS;
import static com.kmfrog.martlet.C.MAX_LEVEL;
import static com.kmfrog.martlet.C.MAX_MAKE_ORDER_VOL_DIFF;
import static com.kmfrog.martlet.C.OPEN_ORDER_TRACKER_SLEEP_MILLIS;
import static com.kmfrog.martlet.C.PREFER_SOURCE;
import static com.kmfrog.martlet.C.PRICE_DIFF;
import static com.kmfrog.martlet.C.SELL_ROBOT_UID;
import static com.kmfrog.martlet.C.SPLIT_TRADE_MAX_DELAY_MILLIS;
import static com.kmfrog.martlet.C.SPLIT_TRADE_RATIO;
import static com.kmfrog.martlet.C.SPLIT_TRADE_SYMBOLS;
import static com.kmfrog.martlet.C.SPREAD_LOWLIMIT_MILLESIMAL;
import static com.kmfrog.martlet.C.TRADE_AVG_WINDOW_MILLIS;
import static com.kmfrog.martlet.C.TRADE_FEED_HOST;
import static com.kmfrog.martlet.C.TRADE_FEED_IO_THREAD_CNT;
import static com.kmfrog.martlet.C.TRADE_FEED_PORT;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.domain.JsonInstrument;
import com.kmfrog.martlet.feed.domain.VolumeStrategy;
import com.kmfrog.martlet.maker.core.Workbench;
import com.kmfrog.martlet.maker.service.DepthService;
import com.kmfrog.martlet.maker.service.TradeService;

@Component
public class SpringContext implements CommandLineRunner {
    @Autowired
    DepthService depthService;

    @Autowired
    TradeService tradeService;

    @Value(DEPTH_FEED_HOST)
    private String depthFeedHost;
    @Value(DEPTH_FEED_PORT)
    private int depthFeedPort;
    @Value(DEPTH_FEED_IO_THREAD_CNT)
    private int depthFeedThreadCnt;
    @Value(TRADE_FEED_HOST)
    private String tradeFeedHost;
    @Value(TRADE_FEED_PORT)
    private int tradeFeedPort;
    @Value(TRADE_FEED_IO_THREAD_CNT)
    private int tradeFeedThreadCnt;

    @Value(OPEN_ORDER_TRACKER_SLEEP_MILLIS)
    private long openOrderTrackerSleepMillis;

    @Value(PREFER_SOURCE)
    private String preferSourceName;

    @Value(ALL_SOURCES)
    private String[] allSource;

    @Value(TRADE_AVG_WINDOW_MILLIS)
    private long tradeAvgWindowMillis;

    @Value(SPREAD_LOWLIMIT_MILLESIMAL)
    private int spreadLowLimitMillesimal;

    @Value(MAKER_SLEEP_MILLIS)
    private long makerSleepMillis;

    @Value(MAX_MAKE_ORDER_VOL_DIFF)
    private double maxMakeOrderVolDiff;

    @Value(PRICE_DIFF)
    private double maxPriceDiff;

    @Value(MAX_LEVEL)
    private int maxLevel;

    @Value(MAX_DELAY_MILLIS)
    private long maxDelayMillis;

    @ApolloJsonValue(ALL_SUPPORTED_SYMBOLS)
    private List<JsonInstrument> supportedInstruments;

    @ApolloJsonValue(DEPTH_SIZE_STRATEGY)
    private List<VolumeStrategy> volumeStrategies;

    @Value(SPLIT_TRADE_RATIO)
    private double splitTradeRatio;

    @ApolloJsonValue(SPLIT_TRADE_SYMBOLS)
    private Set<String> splitTradeSymbols;

    @Value(SPLIT_TRADE_MAX_DELAY_MILLIS)
    private long splitTradeMaxDelayMillis;

    @Value(MAKER_TRADE_USER_ID)
    private int makerTradeUserId;

    @Value(BUY_ROBOT_UID)
    private int buyRobotId;

    @Value(SELL_ROBOT_UID)
    private int sellRobotId;

    private Workbench workbench;

    public void run(String... args) throws Exception {

        Map<String, Instrument> allInstrumentMap = supportedInstruments.stream()
                .collect(Collectors.toMap(JsonInstrument::getName,
                        v -> new Instrument(v.getName().toUpperCase(), v.getP(), v.getV(), v.getShowPrice())));
        // Set<String> allSymbol = allInstrumentMap.keySet();
        // Set<String> supportedSymbol = FeedUtils.parseInstrumentName(C.BINANCE_INSTRUMENTS);
        workbench = new Workbench(depthService, tradeService, this);
        workbench.start(allInstrumentMap);
    }

    public DepthService getDepthService() {
        return depthService;
    }

    public void setDepthService(DepthService depthService) {
        this.depthService = depthService;
    }

    public TradeService getTradeService() {
        return tradeService;
    }
    
    @ApolloConfigChangeListener
    private void onChange(ConfigChangeEvent changeEvent) {
        // ${xxx} -> xxx
        String supportedSymbolKey = trimPlaceHolder(ALL_SUPPORTED_SYMBOLS);
        if (changeEvent.isChanged(supportedSymbolKey)) {
            reloadAllSupportedSymbols(changeEvent, supportedSymbolKey);
        }

        Integer v = reloadInt(SPREAD_LOWLIMIT_MILLESIMAL, changeEvent);
        if (v != null) {
            spreadLowLimitMillesimal = v;
        }

        String vStr = reloadString(PREFER_SOURCE, changeEvent);
        if (vStr != null) {
            preferSourceName = vStr;
        }

        Long vLng = reloadLong(MAKER_SLEEP_MILLIS, changeEvent);
        if (vLng != null) {
            makerSleepMillis = vLng;
        }

        Double vDouble = reloadDouble(MAX_MAKE_ORDER_VOL_DIFF, changeEvent);
        if (vDouble != null) {
            maxMakeOrderVolDiff = vDouble;
        }

        vDouble = reloadDouble(PRICE_DIFF, changeEvent);
        if (vDouble != null) {
            maxPriceDiff = vDouble;
        }

        v = reloadInt(MAX_LEVEL, changeEvent);
        if (v != null) {
            maxLevel = v;
        }

        vLng = reloadLong(MAX_DELAY_MILLIS, changeEvent);
        if (vLng != null) {
            maxDelayMillis = vLng;
        }

        vDouble = reloadDouble(SPLIT_TRADE_RATIO, changeEvent);
        if (vDouble != null) {
            splitTradeRatio = vDouble;
        }

        vLng = reloadLong(SPLIT_TRADE_MAX_DELAY_MILLIS, changeEvent);
        if (vLng != null) {
            splitTradeMaxDelayMillis = vLng;
        }

        Set<String> listStr = reloadStringSet(SPLIT_TRADE_SYMBOLS, changeEvent);
        if (listStr != null) {
            splitTradeSymbols = listStr;
        }

    }

    private String trimPlaceHolder(String constants) {
        int endIndex = constants.indexOf(':') > 0 ? constants.indexOf(":") : constants.length() - 1;
        return constants.substring(2, endIndex);
    }

    private Integer reloadInt(String constants, ConfigChangeEvent changeEvent) {
        String key = trimPlaceHolder(constants);
        if (changeEvent.isChanged(key)) {
            return Integer.valueOf(changeEvent.getChange(key).getNewValue());
        }
        return null;
    }

    private Set<String> reloadStringSet(String constants, ConfigChangeEvent changeEvent) {
        String key = trimPlaceHolder(constants);
        if (changeEvent.isChanged(key)) {
            String v = changeEvent.getChange(key).getNewValue();
            if (v != null) {
                String[] arr = v.split(",");
                Set<String> set = new HashSet<>();
                set.addAll(Arrays.asList(arr));
                return set;
            }
        }
        return null;
    }

    private Long reloadLong(String constants, ConfigChangeEvent changeEvent) {
        String key = trimPlaceHolder(constants);
        if (changeEvent.isChanged(key)) {
            return Long.valueOf(changeEvent.getChange(key).getNewValue());
        }
        return null;
    }

    private Double reloadDouble(String constants, ConfigChangeEvent changeEvent) {
        String key = trimPlaceHolder(constants);
        if (changeEvent.isChanged(key)) {
            return Double.valueOf(changeEvent.getChange(key).getNewValue());
        }
        return null;
    }

    private String reloadString(String constants, ConfigChangeEvent changeEvent) {
        String key = trimPlaceHolder(constants);
        if (changeEvent.isChanged(key)) {
            return changeEvent.getChange(key).getNewValue();
        }
        return null;
    }

    private void reloadAllSupportedSymbols(ConfigChangeEvent changeEvent, String supportedSymbolKey) {
        ConfigChange symbolChange = changeEvent.getChange(supportedSymbolKey);
        String newValue = symbolChange.getNewValue();
        Gson gson = new Gson();
        supportedInstruments = gson.fromJson(newValue,
                TypeToken.getParameterized(List.class, JsonInstrument.class).getType());
        Map<String, Instrument> allInstrumentMap = supportedInstruments.stream().collect(Collectors
                .toMap(JsonInstrument::getName, v -> new Instrument(v.getName().toUpperCase(), v.getP(), v.getV(), v.getShowPrice())));
        // Set<String> allSymbol = allInstrumentMap.keySet();
        // Set<String> supportedSymbol = FeedUtils.parseInstrumentName(C.BINANCE_INSTRUMENTS);
        workbench.start(allInstrumentMap);
    }

    public long getOpenOrderTrackerSleepMillis() {
        return openOrderTrackerSleepMillis;
    }

    public String getPreferSourceName() {
        return preferSourceName;
    }

    public Source[] getAllSource() {
        Source[] sources = new Source[allSource.length];
        for (int i = 0; i < allSource.length; i++) {
            sources[i] = Source.valueOf(allSource[i]);
        }
        return sources;
    }

    public int getSpreadLowLimitMillesimal() {
        return spreadLowLimitMillesimal;
    }

    public long getMakerSleepMillis() {
        return makerSleepMillis;
    }

    public double getMaxMakeOrderVolDiff() {
        return maxMakeOrderVolDiff;
    }

    public double getMaxPriceDiff() {
        return maxPriceDiff;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public long getMaxDelayMillis() {
        return maxDelayMillis;
    }

    public List<JsonInstrument> getSupportedInstruments() {
        return supportedInstruments;
    }

    public List<VolumeStrategy> getVolumeStrategies() {
        return volumeStrategies;
    }

    public double getSplitTradeRatio() {
        return splitTradeRatio;
    }

    public Set<String> getSplitTradeSymbols() {
        return splitTradeSymbols;
    }

    public long getSplitTradeMaxDelayMillis() {
        return splitTradeMaxDelayMillis;
    }

    public String getDepthFeedHost() {
        return depthFeedHost;
    }

    public int getDepthFeedPort() {
        return depthFeedPort;
    }

    public int getDepthFeedThreadCnt() {
        return depthFeedThreadCnt;
    }

    public String getTradeFeedHost() {
        return tradeFeedHost;
    }

    public int getTradeFeedPort() {
        return tradeFeedPort;
    }

    public int getTradeFeedThreadCnt() {
        return tradeFeedThreadCnt;
    }

    public long getTradeAvgWindowMillis() {
        return tradeAvgWindowMillis;
    }

    public int getMakerTradeUserId() {
        return makerTradeUserId;
    }

    public int getSellRobotId() {
        return sellRobotId;
    }

    public int getBuyRobotId() {
        return buyRobotId;
    }

}
