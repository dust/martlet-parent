package com.kmfrog.martlet.maker.exec;


public abstract class Exec implements Runnable, Comparable<Exec> {

    final long createAt;

    Exec(long createAt) {
        this.createAt = createAt;
    }

    long getCreateAt() {
        return createAt;
    }

    public int compareTo(Exec o) {
        if (o == null) {
            return 1;
        }
        return (int) (createAt - o.createAt);
    }

}
