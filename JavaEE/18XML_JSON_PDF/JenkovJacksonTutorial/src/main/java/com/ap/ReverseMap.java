package com.ap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ReverseMap {
    public static void main(String[] args) {
        System.out.println("last 10 elements from linkedhashmap");

        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("g", 1000);
        map.put("a", 222);
        map.put("z", 333);
        map.put("k", 4444);
        map.put("e", 5555);


        System.out.println(lastNFromMap(map, 2));
        System.out.println(subMap(map, 1, 3));
    }

    public static Map<String, Integer> lastNFromMap(LinkedHashMap<String, Integer> map, int n){
        if(n > map.size()){
            n = map.size();
        }
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        map.keySet().stream().skip(map.size()-n).forEach(k->{
            result.put(k, map.get(k));
        });

        return result;
    }

    public static Map<String, Integer> subMap(LinkedHashMap<String, Integer> map, int n, int m){
        AtomicInteger counter = new AtomicInteger(0);
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        map.keySet().stream().skip(n).forEach(k->{
            if(m > counter.get()){
                counter.incrementAndGet();
                result.put(k, map.get(k));
            }
        });

        return result;
    }


}