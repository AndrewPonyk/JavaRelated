package com.ap;

import java.io.IOException;
import java.net.InetSocketAddress;

import net.spy.memcached.MemcachedClient;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException
    {
        String[] top10CitiesInTheWorldByPopulation = {"Tokyo", "Delhi", "Shanghai", "Sao Paulo", "Mexico City", "Cairo", "Mumbai", "Beijing", "Dhaka", "Osaka"};
        for (int i = 0; i < top10CitiesInTheWorldByPopulation.length; i++) {
            System.out.println(top10CitiesInTheWorldByPopulation[i]);
        }
        
        MemcachedClient memcachedClient = new MemcachedClient(new InetSocketAddress("localhost", 11211));

        memcachedClient.set("myKey", 3600, "Hello, Memcached!");

        // Retrieve and output the value from cache
        System.out.println("myKey = " + memcachedClient.get("myKey"));

    }
} 
