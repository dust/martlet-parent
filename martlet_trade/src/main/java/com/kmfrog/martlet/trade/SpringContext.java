package com.kmfrog.martlet.trade;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.trade.config.InstrumentsJson;
import com.kmfrog.martlet.trade.config.InstrumentsJson.JsonInstrument;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;

import io.broker.api.client.BrokerApiClientFactory;
import io.broker.api.client.BrokerApiRestClient;

@Component
class SpringContext implements CommandLineRunner {

    @Value("${api.base.url}")
    private String baseUrl;

    @Value("${api.key}")
    private String apiKey;

    @Value("${api.secret}")
    private String secret;

    @Value("${pub.depth.host}")
    private String pubDepthHost;

    @Value("${pub.depth.port}")
    private int pubDepthPort;

    @Value("${pub.trade.host}")
    private String pubTradeHost;

    @Value("${pub.trade.port}")
    private int pubTradePort;

    @Value("${pub.depth.io.thread.cnt}")
    private int pubDepthIoThreadCnt;

    @Value("${pub.trade.io.thread.cnt}")
    private int pubTradeIoThreadCnt;

    @Value("${hedge.items}")
    private String[] hedgeEntrys;

    @Value("${bhex.ws.url")
    private String hbExWsUrl;

    @Value("${bhex.depth.fmt")
    private String hbDepthFmt;

//    @Autowired
//    InstrumentArgs instrumentArgs;

    @Autowired
    InstrumentsJson instrumentJson;

    public SpringContext() {
    }

    @Override
    public void run(String... args) throws Exception {
        Workbench app = new Workbench();

        BrokerApiRestClient client = BrokerApiClientFactory.newInstance(baseUrl, apiKey, secret).newRestClient();
        List<JsonInstrument> jsonInstruments = instrumentJson.getSymbols();
        // Map<String, >
//        List<InstrumentArgs.InstrumentArg> params = instrumentArgs.getItems();
        List<Param> params = instrumentJson.getArgs();
//        Map<String, Instrument> instrumentMap = new HashMap<String, Instrument>();
//        Map<String, Param> instrumentArgsMap = new HashMap<String, Param>();
//        for(JsonInstrument item : jsonInstruments) {
//            String name = item.getName();
//        }
        Map<String, Instrument> instrumentMap = jsonInstruments.stream().collect(Collectors.toMap(
                JsonInstrument::getName, v -> new Instrument(v.getName(), v.getP(), v.getV(), v.getShowPrice())));
        Map<String, Param> instrumentArgsMap = params.stream()
                .collect(Collectors.toMap(Param::getName, v -> v));
        app.start(Source.Bhex, hedgeEntrys, instrumentMap, instrumentArgsMap, client);

        // app.startHedgeInstrument(Source.Bhex, hntcbtc, buildConfigArgs(4000, 19000, 50000, 13390000), client);
        // InstrumentArg[] items = instrumentJson.get
        // System.out.println("instrumentArgs"+items);
        // Map<String, Object> cfgArgs = FeedUtils.parseConfigArgs(cfg.getString("hedge.args"));
        // List<Instrument> hedgeInstruments = FeedUtils.parseInstruments(cfg.getString("instruments"));
        // List<Instrument> occupyInstruments = FeedUtils.parseInstruments(cfg.getString("triangle.instruments"));
        // List<Instrument> all = new ArrayList<>();
        // all.addAll(hedgeInstruments);
        // all.addAll(occupyInstruments);
        //
        // // app.start(Source.Bhex, hedgeInstruments, all, cfgArgs, client);
        // Instrument ca = occupyInstruments.get(0);
        //// Instrument ab = occupyInstruments.get(1);
        // Instrument cb = occupyInstruments.get(2);
        // Map<String, String> caArgs = (Map<String, String>) cfgArgs.get(ca.asString());
        // Map<String, String> cbArgs = (Map<String, String>) cfgArgs.get(cb.asString());
        //
        // app.startOccupyInstrument(Source.Bhex, ca, ab, cb, client, caArgs, cbArgs);
        // app.startHedgeInstrument(Source.Bhex, ca, caArgs, client);
        // app.startHedgeInstrument(Source.Bhex, cb, cbArgs, client);

        // app.startOpenOrderTracker(Source.Bhex, all.toArray(new Instrument[all.size()]), client);

    }

    // Map<String, String> buildConfigArgs(int minSleepMillis, int maxSleepMillis, long vMin, long vMax) {
    // Map<String, String> cfg = new HashMap<String, String>();
    // cfg.put("minSleepMillis", String.valueOf(minSleepMillis));
    // cfg.put("maxSleepMillis", String.valueOf(maxSleepMillis));
    // cfg.put("vMin", String.valueOf(vMin));
    // cfg.put("vMax", String.valueOf(vMax));
    // return cfg;
    // }

}
