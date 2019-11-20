package com.kmfrog.martlet.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.kmfrog.martlet.book.Instrument;
import com.kmfrog.martlet.feed.Source;
import com.kmfrog.martlet.util.FeedUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import io.broker.api.client.BrokerApiClientFactory;
import io.broker.api.client.BrokerApiRestClient;

@Component
class SpringContext implements CommandLineRunner {

    public SpringContext() {
    }

    @Override
    public void run(String... args) throws Exception {
        Workbench app = new Workbench();
        Config cfg = ConfigFactory.load();
        String baseUrl = cfg.getString("api.base.url");
        String apiKey = cfg.getString("api.key");
        String secret = cfg.getString("api.secret");

        BrokerApiRestClient client = BrokerApiClientFactory.newInstance(baseUrl, apiKey, secret).newRestClient();
        Map<String, Object> cfgArgs = FeedUtils.parseConfigArgs(cfg.getString("hedge.args"));
        List<Instrument> hedgeInstruments = FeedUtils.parseInstruments(cfg.getString("instruments"));
        List<Instrument> occupyInstruments = FeedUtils.parseInstruments(cfg.getString("triangle.instruments"));
        List<Instrument> all = new ArrayList<>();
        all.addAll(hedgeInstruments);
        all.addAll(occupyInstruments);
        // app.start(Source.Bhex, hedgeInstruments, all, cfgArgs, client);
        Instrument ca = occupyInstruments.get(0);
        Instrument ab = occupyInstruments.get(1);
        Instrument cb = occupyInstruments.get(2);
        Map<String, String> caArgs = (Map<String, String>) cfgArgs.get(ca.asString());
        Map<String, String> cbArgs = (Map<String, String>) cfgArgs.get(cb.asString());
        app.startOccupyInstrument(Source.Bhex, ca, ab, cb, client, caArgs, cbArgs);
        app.startOpenOrderTracker(Source.Bhex, all.toArray(new Instrument[all.size()]), client);

    }

}
