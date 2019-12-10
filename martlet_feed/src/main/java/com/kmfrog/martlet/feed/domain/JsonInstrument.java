package com.kmfrog.martlet.feed.domain;

public class JsonInstrument {

    private String name;
    private int p;
    private int v;
    private int showPrice;

    public JsonInstrument() {

    }

    public String getName() {
        return name.toLowerCase();
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + p;
        result = prime * result + v;
        result = prime * result + showPrice;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        JsonInstrument other = (JsonInstrument) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (p != other.p)
            return false;
        if (v != other.v)
            return false;
        if (showPrice != other.showPrice) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "JsonInstrument [name=" + name + ", p=" + p + ", v=" + v + "]";
    }

}
