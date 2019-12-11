package com.kmfrog.martlet.feed;

import static com.kmfrog.martlet.C.ALL_SUPPORTED_SYMBOLS;
import static com.kmfrog.martlet.C.BHEX_DEPTH_FMT;
import static com.kmfrog.martlet.C.BHEX_WS_URL;
import static com.kmfrog.martlet.C.BIKUN_DEPTH_FMT;
import static com.kmfrog.martlet.C.BIKUN_SUPPORTED;
import static com.kmfrog.martlet.C.BIKUN_WS_URL;
import static com.kmfrog.martlet.C.BINANCE_REST_URL;
import static com.kmfrog.martlet.C.BINANCE_SUPPORTED;
import static com.kmfrog.martlet.C.BINANCE_WS_DEPTH;
import static com.kmfrog.martlet.C.BINANCE_WS_TRADE;
import static com.kmfrog.martlet.C.MONITOR_SLEEP_MILLIS;
import static com.kmfrog.martlet.C.PUB_DEPTH_HOST;
import static com.kmfrog.martlet.C.PUB_DEPTH_IO_THREAD_CNT;
import static com.kmfrog.martlet.C.PUB_DEPTH_PORT;
import static com.kmfrog.martlet.C.PUB_TRADE_HOST;
import static com.kmfrog.martlet.C.PUB_TRADE_PORT;
import static com.kmfrog.martlet.C.TAC_SUPPORTED;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.model.ConfigChange;
import com.ctrip.framework.apollo.model.ConfigChangeEvent;
import com.ctrip.framework.apollo.spring.annotation.ApolloConfigChangeListener;
import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.domain.JsonInstrument;

@Component
public class SpringContext implements CommandLineRunner {

//    Config cfg;

    @Value(PUB_DEPTH_HOST)
    private String pubDepthHost;
    
    @Value(PUB_DEPTH_PORT)
    private int pubDepthPort;
    
    @Value(PUB_TRADE_HOST)
    private String pubTradeHost;
    
    @Value(PUB_TRADE_PORT)
    private int pubTradePort;
    
    @Value(PUB_DEPTH_IO_THREAD_CNT)
    private int depthIoThreadCnt;
    
    @Value(PUB_DEPTH_IO_THREAD_CNT)
    private int tradeIoThreadCnt;

    @ApolloJsonValue(ALL_SUPPORTED_SYMBOLS)
    private List<JsonInstrument> supportedInstruments;

    @ApolloJsonValue(BINANCE_SUPPORTED)
    private Set<String> binanceSupportedSymbols;
    
    @ApolloJsonValue(BIKUN_SUPPORTED)
    private Set<String> bikunSupportedSymbols;
    
    @Value(BINANCE_REST_URL)
    private String binanceRestUrl;
    
    @Value(BINANCE_WS_DEPTH)
    private String binanceDepthUrl;
    
    @Value(BINANCE_WS_TRADE)
    private String binanceTradeUrl;
    
    @Value(BIKUN_WS_URL)
    private String bikunWsUrl;
    
    @Value(BIKUN_DEPTH_FMT)
    private String bikunDepthFmt;
    
    @Value(BHEX_WS_URL)
    private String tacWsUrl;
    
    @Value(BHEX_DEPTH_FMT)
    private String tacDepthFmt;
    
    @ApolloJsonValue(TAC_SUPPORTED)
    private Set<String> tacSupportedSymbols;
    
    @Value(MONITOR_SLEEP_MILLIS)
    private long monitorSleepMillis;
    
    private Workbench workbench;

    public SpringContext() {
        // String host = pubDepthHost;
        // cfg = ConfigFactory.load();
        // cfg.checkValid(reference, restrictToPaths);
    }

    @Override
    public void run(String... args) throws Exception {

        Map<String, Instrument> allInstrumentMap = supportedInstruments.stream()
                .collect(Collectors.toMap(JsonInstrument::getName,
                        v -> new Instrument(v.getName().toUpperCase(), v.getP(), v.getV(), v.getShowPrice())));
        Set<String> allSymbol = allInstrumentMap.keySet();
        Set<String> binanceSymbols = Sets.intersection(allSymbol, binanceSupportedSymbols);
//        Set<String> bikunSymbols = Sets.intersection(allSymbol, bikunSupportedSymbols);
//        Set<String> tacSymbols = Sets.intersection(allSymbol,  tacSupportedSymbols);
//        Set<String> huobiSymbols = Sets.intersection(allSymbol, huobiSupportedSymbols);
//        Set<String> okexSymbols = Sets.intersection(allSymbol, okexSupportedSymbols);

        // List<Instrument> instruments = FeedUtils.parseInstruments(cfg.getString("instruments"));
        // List<Instrument> triangleInstruments = FeedUtils.parseInstruments(cfg.getString("triangle.instruments"));
        // List<Instrument> bikunInstruments = FeedUtils.parseInstruments(cfg.getString("bikun.instruments"));
        // List<Instrument> loexInstruments = FeedUtils.parseInstruments(cfg.getString("loex.instruments"));

        // Map<String, Object> cfgArgs = FeedUtils.parseConfigArgs(cfg.getString("hedge.args"));
//        Map<String, List<Instrument>> all = new HashMap<String, List<Instrument>>();
//        List<Instrument> bhexInstruments = new ArrayList<>();
//        bhexInstruments.addAll(instruments);
//        bhexInstruments.addAll(triangleInstruments);
//        all.put(Source.Bhex.name(), bhexInstruments);
//        all.put(Source.Bikun.name(), bikunInstruments);
//        all.put(Source.Loex.name(), loexInstruments);

        workbench = new Workbench(this);
        workbench.start(allInstrumentMap, binanceSymbols);

        // Map<String, Instrument> instrumentMap = instruments.stream().collect(Collectors.toMap(Instrument::asString,
        // v->v));

    }

    public String getPubDepthHost() {
        return pubDepthHost;
    }

    public int getPubDepthPort() {
        return pubDepthPort;
    }

    public String getPubTradeHost() {
        return pubTradeHost;
    }

    public int getPubTradePort() {
        return pubTradePort;
    }

    public int getDepthIoThreadCnt() {
        return depthIoThreadCnt;
    }

    public int getTradeIoThreadCnt() {
        return tradeIoThreadCnt;
    }

    public List<JsonInstrument> getSupportedInstruments() {
        return supportedInstruments;
    }

    public Set<String> getBinanceSupportedSymbols() {
        return binanceSupportedSymbols;
    }

    public Set<String> getBikunSupportedSymbols() {
        return bikunSupportedSymbols;
    }

    public Set<String> getTacSupportedSymbols() {
        return tacSupportedSymbols;
    }

    public String getTacWsUrl() {
        return tacWsUrl;
    }

    public String getTacDepthFmt() {
        return tacDepthFmt;
    }

    public String getBikunWsUrl() {
        return bikunWsUrl;
    }

    public String getBikunDepthFmt() {
        return bikunDepthFmt;
    }

    public String getBinanceDepthUrl() {
        return binanceDepthUrl;
    }

    public String getBinanceTradeUrl() {
        return binanceTradeUrl;
    }

    public String getBinanceRestUrl() {
        return binanceRestUrl;
    }

    public Workbench getWorkbench() {
        return workbench;
    }

    public void setWorkbench(Workbench workbench) {
        this.workbench = workbench;
    }

    public long getMonitorSleepMillis() {
        return monitorSleepMillis;
    }
    
    @ApolloConfigChangeListener
    private void onChange(ConfigChangeEvent changeEvent) {
//        String supportedSymbolKey = trimApolloConfigKey(ALL_SUPPORTED_SYMBOLS);
//        String binanceSupportedKey = trimApolloConfigKey(BINANCE_SUPPORTED);
//        String huobiSupportedKey = trimApolloConfigKey(HUOBI_SUPPORTED);
//        String okexSupportedKey = trimApolloConfigKey(OKEX_SUPPORTED);
//        if (changeEvent.isChanged(supportedSymbolKey) || changeEvent.isChanged(binanceSupportedKey)
//                || changeEvent.isChanged(huobiSupportedKey) || changeEvent.isChanged(okexSupportedKey)) {
//            reloadAllSupportedSymbols(changeEvent, supportedSymbolKey);
//            Set<String> symbols = reloadStringSet(changeEvent, binanceSupportedKey);
//            if (symbols != null) {
//                binanceSupportedSymbols = symbols;
//            }
//            symbols = reloadStringSet(changeEvent, huobiSupportedKey);
//            if (symbols != null) {
//                huobiSupportedSymbols = symbols;
//            }
//            symbols = reloadStringSet(changeEvent, okexSupportedKey);
//            if (symbols != null) {
//                okexSupportedSymbols = symbols;
//            }
//            restart();
//            logger.info("apollo.onchange. binance:{}, huobi:{}, okex:{}", binanceSupportedSymbols,
//                    huobiSupportedSymbols, okexSupportedSymbols);
//        }
    }

    private void reloadAllSupportedSymbols(ConfigChangeEvent changeEvent, String supportedSymbolKey) {
        if (changeEvent.isChanged(supportedSymbolKey)) {
            ConfigChange symbolChange = changeEvent.getChange(supportedSymbolKey);
            String newValue = symbolChange.getNewValue();
            Gson gson = new Gson();
            supportedInstruments = gson.fromJson(newValue,
                    TypeToken.getParameterized(List.class, JsonInstrument.class).getType());
        }
    }

    private Set<String> reloadStringSet(ConfigChangeEvent changeEvent, String key) {
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

    @PreDestroy
    public void destroy() {
        workbench.destroy();
    }

    // private Map<Object, Object> buildConfigMap() {
    // Map<Object, Object> ret = new HashMap<>();
    // Map<String, String> binanceMap = new HashMap<>();
    // binanceMap.put(BINANCE_WS_DEPTH, );
    // binanceMap.put(BINANCE_WS_TRADE, binanceWsTradeUrl);
    // binanceMap.put(BINANCE_REST_URL, binanceRestDepthUrl);
    // ret.put(Source.Binance, binanceMap);
    //
    // Map<String, String> huobiMap = new HashMap<>();
    // huobiMap.put(HUOBI_WS_URL, huobiWsUrl);
    // huobiMap.put(HUOBI_DEPTH_FMT, huobiDepthFmt);
    // huobiMap.put(HUOBI_TRADE_FMT, huobiTradeFmt);
    // ret.put(Source.Huobi, huobiMap);
    //
    // Map<String, String> okexMap = new HashMap<>();
    // okexMap.put(OKEX_WS_URL, okexWsUrl);
    // okexMap.put(OKEX_DEPTH_FMT, okexDepthFmt);
    // okexMap.put(OKEX_TRADE_FMT, okexTradeFmt);
    // okexMap.put(OKEX_DEPTH_JOIN_SEPARATOR, okexDepthJoinSeparator);
    // okexMap.put(OKEX_TRADE_JOIN_SEPARATOR, okexTradeJoinSeparator);
    // ret.put(Source.Okex, okexMap);
    //
    // ret.put(PUB_DEPTH_HOST, pubDepthHost);
    // ret.put(PUB_DEPTH_PORT, pubDepthPort);
    // ret.put(PUB_DEPTH_IO_THREAD_CNT, pubDepthThreadCnt);
    // ret.put(PUB_TRADE_HOST, pubTradeHost);
    // ret.put(PUB_TRADE_PORT, pubTradePort);
    // ret.put(PUB_TRADE_IO_THREAD_CNT, pubTradeThreadCnt);
    //
    // return ret;
    // }
    
    

}
