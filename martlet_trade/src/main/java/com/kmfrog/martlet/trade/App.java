package com.kmfrog.martlet.trade;

import com.kmfrog.martlet.feed.MktDataFeed;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        MktDataFeed feed = new MktDataFeed("localhost", 5188, 2);
        feed.start();
        try {
            feed.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
