package com.kmfrog.martlet.trade;

import static com.kmfrog.martlet.C.ALL_SUPPORTED_SYMBOLS;
import static com.kmfrog.martlet.C.MAX_LEVEL;
import static com.kmfrog.martlet.C.SPREAD_LOWLIMIT_MILLESIMAL;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.ctrip.framework.apollo.spring.annotation.ApolloJsonValue;
import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.feed.loex.LoexApiRestClient;
import com.kmfrog.martlet.trade.bikun.BikunApiRestClient;
import com.kmfrog.martlet.trade.config.InstrumentsJson;
import com.kmfrog.martlet.trade.config.InstrumentsJson.JsonInstrument;
import com.kmfrog.martlet.trade.config.InstrumentsJson.Param;
import com.kmfrog.martlet.util.FeedUtils;

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

    @Value("${bhex.ws.url}")
    private String hbExWsUrl;

    @Value("${bhex.depth.fmt}")
    private String hbDepthFmt;
    
    @Value("${bikun.hedge.items}")
    private String[] bikunHedgeEntrys;
    
    @Value("${loex.hedge.items}")
    private String[] loexHedgeEntrys;
    
    @Value("${api.base.url.bikun}")
    private String bikunBaseUrl;
    
    @Value("${api.key.bikun}")
    private String bikunApiKey;
    
    @Value("${api.secret.bikun}")
    private String bikunSecret;
    
    @Value("${api.base.url.loex}")
    private String loexBaseUrl;
    
    @Value("${api.key.loex}")
    private String loexApiKey;
    
    @Value("${api.secret.loex}")
    private String loexSecret;
    
    @Value(SPREAD_LOWLIMIT_MILLESIMAL)
    private int spreadLowLimitMillesimal;
    
    @Value(MAX_LEVEL)
    private int maxLevel;
    
    @ApolloJsonValue("${triangle.instruments}")
    private List<JsonInstrument> tringleInstruments;


//    @Autowired
//    InstrumentArgs instrumentArgs;

    @Autowired
    InstrumentsJson instrumentJson;

    public SpringContext() {
    }

    @Override
    public void run(String... args) throws Exception {
        Workbench app = new Workbench(this);

        BrokerApiRestClient client = BrokerApiClientFactory.newInstance(baseUrl, apiKey, secret).newRestClient();
        BikunApiRestClient bikunClient = new BikunApiRestClient(bikunBaseUrl, bikunApiKey, bikunSecret);
        LoexApiRestClient loexClient = new LoexApiRestClient(loexBaseUrl, loexApiKey, loexSecret);
        
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
//        app.start(Source.Bhex, hedgeEntrys, instrumentMap, instrumentArgsMap, client);
//        app.startBikun(Source.Bikun, bikunHedgeEntrys, instrumentMap, instrumentArgsMap, bikunClient);
        //loex要求停掉,开放时间不确定
//        app.startLoex(Source.Loex, loexHedgeEntrys, instrumentMap, instrumentArgsMap, loexClient);
        app.startOpenOrderTracker(Source.Bhex, instrumentMap.values().toArray(new Instrument[instrumentMap.size()]), client);
        
        Instrument ca = new Instrument(tringleInstruments.get(0).getName(), tringleInstruments.get(0).getP(), tringleInstruments.get(0).getV(), tringleInstruments.get(0).getShowPrice()); 
        Instrument ab = new Instrument(tringleInstruments.get(1).getName(), tringleInstruments.get(1).getP(), tringleInstruments.get(1).getV(), tringleInstruments.get(1).getShowPrice());
        Instrument cb = new Instrument(tringleInstruments.get(2).getName(), tringleInstruments.get(2).getP(), tringleInstruments.get(2).getV(), tringleInstruments.get(2).getShowPrice());
        app.startOccupyInstrument(Source.Bhex, ca, ab, cb, client, instrumentArgsMap.get(ca.asString()), instrumentArgsMap.get(cb.asString()));

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


    }
    
    public int getSpreadLowLimitMillesimal() {
        return spreadLowLimitMillesimal;
    }

    public int getMaxLevel() {
        return maxLevel;
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
