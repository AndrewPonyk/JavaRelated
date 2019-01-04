package com.ap._6JacksonAnnotations;

import javafx.util.Pair;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public class SortMap {
    public static void main(String[] args) {
        System.out.println("Sort linked hashmap");
        LinkedHashMap<String, Pair<Integer, Integer>> map = new LinkedHashMap<>();

        map.put("111", new Pair<>(30,33));
        map.put("3333", new Pair<>(30,33));
        map.put("222", new Pair<>(10, 7));
        map.put("333", new Pair<>(1, 1));

        ValueComparator vcp = new ValueComparator(map);
        TreeMap<String, Pair<Integer, Integer>> sortedMap = new TreeMap<String, Pair<Integer, Integer>>(vcp);
        sortedMap.putAll(map);

        System.out.println(sortedMap);
    }

    public static class ValueComparator implements Comparator<String> {
        final Map<String, Pair<Integer, Integer>> base;

        public ValueComparator(Map<String, Pair<Integer, Integer>> base) {
            this.base = base;
        }

        @Override
        public int compare(String o1, String o2) {
            return           -1*  Double.compare((double)base.get(o1).getKey()/base.get(o1).getValue(),
                    (double)base.get(o2).getKey()/base.get(o2).getValue());
        }

        @Override
        public boolean equals(Object obj) {
            if(obj instanceof Pair){
                return false;
            }
            return this == obj;
        }
    }
}