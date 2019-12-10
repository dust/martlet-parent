package com.kmfrog.martlet.feed;

import static com.kmfrog.martlet.C.ALL_SUPPORTED_SYMBOLS;
import static com.kmfrog.martlet.C.BIKUN_SUPPORTED;
import static com.kmfrog.martlet.C.BINANCE_SUPPORTED;
import static com.kmfrog.martlet.C.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import com.google.common.collect.Sets;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.domain.JsonInstrument;
import com.typesafe.config.Config;

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
    private String pubTradePort;
    
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
    
    @ApolloJsonValue(TAC_SUPPORTED)
    private Set<String> tacSupportedSymbols;
    
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

    public void setPubDepthHost(String pubDepthHost) {
        this.pubDepthHost = pubDepthHost;
    }

    public int getPubDepthPort() {
        return pubDepthPort;
    }

    public void setPubDepthPort(int pubDepthPort) {
        this.pubDepthPort = pubDepthPort;
    }

    public String getPubTradeHost() {
        return pubTradeHost;
    }

    public void setPubTradeHost(String pubTradeHost) {
        this.pubTradeHost = pubTradeHost;
    }

    public String getPubTradePort() {
        return pubTradePort;
    }

    public void setPubTradePort(String pubTradePort) {
        this.pubTradePort = pubTradePort;
    }

    public int getDepthIoThreadCnt() {
        return depthIoThreadCnt;
    }

    public void setDepthIoThreadCnt(int depthIoThreadCnt) {
        this.depthIoThreadCnt = depthIoThreadCnt;
    }

    public int getTradeIoThreadCnt() {
        return tradeIoThreadCnt;
    }

    public void setTradeIoThreadCnt(int tradeIoThreadCnt) {
        this.tradeIoThreadCnt = tradeIoThreadCnt;
    }

    public List<JsonInstrument> getSupportedInstruments() {
        return supportedInstruments;
    }

    public void setSupportedInstruments(List<JsonInstrument> supportedInstruments) {
        this.supportedInstruments = supportedInstruments;
    }

    public Set<String> getBinanceSupportedSymbols() {
        return binanceSupportedSymbols;
    }

    public void setBinanceSupportedSymbols(Set<String> binanceSupportedSymbols) {
        this.binanceSupportedSymbols = binanceSupportedSymbols;
    }

    public Set<String> getBikunSupportedSymbols() {
        return bikunSupportedSymbols;
    }

    public void setBikunSupportedSymbols(Set<String> bikunSupportedSymbols) {
        this.bikunSupportedSymbols = bikunSupportedSymbols;
    }

    public Set<String> getTacSupportedSymbols() {
        return tacSupportedSymbols;
    }

    public void setTacSupportedSymbols(Set<String> tacSupportedSymbols) {
        this.tacSupportedSymbols = tacSupportedSymbols;
    }

    public Workbench getWorkbench() {
        return workbench;
    }

    public void setWorkbench(Workbench workbench) {
        this.workbench = workbench;
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
