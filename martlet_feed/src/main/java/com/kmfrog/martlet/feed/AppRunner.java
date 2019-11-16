package com.kmfrog.martlet.feed;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.util.FeedUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Component
public class AppRunner implements CommandLineRunner {

    Config cfg;

    public AppRunner() {
        cfg = ConfigFactory.load();
        // cfg.checkValid(reference, restrictToPaths);
    }

    @Override
    public void run(String... args) throws Exception {
        String baseUrl = cfg.getString("api.base.url");
        String apiKey = cfg.getString("api.key");
        String secret = cfg.getString("api.secret");
        List<Instrument> instruments = FeedUtils.parseInstruments(cfg.getString("instruments"));
        List<Instrument> triangleInstruments = FeedUtils.parseInstruments(cfg.getString("triangle.instruments"));
        // Map<String, Object> cfgArgs = FeedUtils.parseConfigArgs(cfg.getString("hedge.args"));
        List<Instrument> all = new ArrayList<>();
        all.addAll(instruments);
        all.addAll(triangleInstruments);
        Workbench workbench = new Workbench(cfg);
        workbench.start(all);

        // Map<String, Instrument> instrumentMap = instruments.stream().collect(Collectors.toMap(Instrument::asString,
        // v->v));

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
