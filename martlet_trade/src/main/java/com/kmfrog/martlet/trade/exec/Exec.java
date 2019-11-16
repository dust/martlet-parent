package com.kmfrog.martlet.trade.exec;

public abstract class Exec implements Comparable<Exec>, Runnable {

    final long createAt;
    
    public Exec(long createAt) {
        this.createAt = createAt;
    }
    
    public long getCreateAt() {
        return createAt;
    }

    @Override
    public int compareTo(Exec o) {
        if(o==null) {
            return 1;
        }
        return (int)(this.createAt - o.createAt);
    }
    
    

}
