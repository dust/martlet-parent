package com.kmfrog.martlet.trade.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource(value = "classpath:instruments.json", factory = JsonPropertySourceFactory.class)
@ConfigurationProperties
public class InstrumentsJson {

    private List<JsonInstrument> symbols;

    private List<Param> args;

    public List<JsonInstrument> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<Map<String, Object>> jsonSymbols) {
        List<JsonInstrument> list = new ArrayList<JsonInstrument>();
        jsonSymbols.stream().forEach(v -> {
            list.add(new JsonInstrument(v));
        });
        this.symbols = list;
    }

    public List<Param> getArgs() {
        return args;
    }

    public void setArgs(List<Map<String, Object>> args) {
        List<Param> list = new ArrayList<>();
        args.stream().forEach(v -> {
            list.add(new Param(v));
        });
        this.args = list;
    }

    public static class Param {
        private String name;
        private int minSleepMillis;
        private int maxSleepMillis;
        private long minVolume;
        private long maxVolume;
        private BigDecimal originBaseVolume;
        private BigDecimal originQuoteVolume;

       
        public Param() {

        }

        public Param(Map<String, Object> params) {
            this.name =(String)params.get("name");
            this.minSleepMillis = (Integer)params.get("minSleepMillis");
            this.maxSleepMillis =  (Integer)params.get("maxSleepMillis");
            this.minVolume = ((Integer)params.get("minVolume")).longValue();
            this.maxVolume = ((Integer)params.get("maxVolume")).longValue();
            this.originBaseVolume = new BigDecimal((String)params.get("originBaseVolume"));
            this.originQuoteVolume = new BigDecimal((String)params.get("originQuoteVolume"));
        }
        
        public BigDecimal getOriginBaseVolume() {
            return originBaseVolume;
        }

        public void setOriginBaseVolume(BigDecimal originBaseVolume) {
            this.originBaseVolume = originBaseVolume;
        }

        public BigDecimal getOriginQuoteVolume() {
            return originQuoteVolume;
        }

        public void setOriginQuoteVolume(BigDecimal originQuoteVolume) {
            this.originQuoteVolume = originQuoteVolume;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getMinSleepMillis() {
            return minSleepMillis;
        }

        public void setMinSleepMillis(int minSleepMillis) {
            this.minSleepMillis = minSleepMillis;
        }

        public int getMaxSleepMillis() {
            return maxSleepMillis;
        }

        public void setMaxSleepMillis(int maxSleepMillis) {
            this.maxSleepMillis = maxSleepMillis;
        }

        public long getMinVolume() {
            return minVolume;
        }

        public void setMinVolume(long minVolume) {
            this.minVolume = minVolume;
        }

        public long getMaxVolume() {
            return maxVolume;
        }

        public void setMaxVolume(long maxVolume) {
            this.maxVolume = maxVolume;
        }

    }

    public static class JsonInstrument {

        private String name;
        private int p;
        private int v;
        private int showPrice;

        public JsonInstrument() {

        }

        public JsonInstrument(Map<String, Object> jsonMap) {
            this.name = (String)jsonMap.get("name");
            this.p = (Integer)jsonMap.get("p");
            this.v = (Integer)jsonMap.get("v");
            this.showPrice = (Integer)jsonMap.get("showPrice");
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getP() {
            return p;
        }

        public void setP(int p) {
            this.p = p;
        }

        public int getV() {
            return v;
        }

        public void setV(int v) {
            this.v = v;
        }

        public int getShowPrice() {
            return showPrice;
        }

        public void setShowPrice(int showPrice) {
            this.showPrice = showPrice;
        }

    }

}
