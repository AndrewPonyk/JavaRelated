package part4StringsBitsAndOther;

import java.util.*;
import java.util.stream.Stream;

public class MapEnhancements {
    public static void main(String[] args) {
        System.out.println("Java 8 map enhancements");

        // New method 'forEach()'
        // New method 'getOrDefault'
        // New mthod 'putIfAbsent()'
        // New signature of remove(key, value)
        // New methods compute computeIfPresent computeIfAbsent
        // New mothod merge

        // ==============================================
        Map<String, Integer> map = new HashMap<>();
        map.put("Lviv", 800000);
        map.put("Kyiv", 2600000);
        map.put("Kharkiv", 1001000);

        System.out.println("Get or default");
        System.out.println(map.getOrDefault("Odesa", 0));

        System.out.println("\nReplace values in map");
        map.replaceAll((s, integer) -> integer > 1000000 ? integer + 2 : integer);
        map.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        });

        System.out.println("\nRemove some elements from map");
        // Old way or removing
        /*Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, Integer> elem = iterator.next();
            if(elem.getValue() > 1000000){
                iterator.remove();
            }
        }*/

        // New Java 8 way of removing from map
        map.entrySet().removeIf(e -> e.getValue() > 1000000);

        map.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        });

        /* If the specified key is not already associated with a value or is
                * associated with null, associates it with the given non-null value.
                * Otherwise, replaces the associated value with the results of the given
                * remapping function, or removes if the result is {@code null}. This
                * method may be of use when combining multiple mapped values for a key.
        * For example, to either create or append a {@code String msg} to a
        * value mapping:*/
        System.out.println("\nMap 'merge()' method example");
        map.merge("Aby-Dabi", 1000000, (e, v) -> e + v); // e is old value and v is new, so we 'merge' this value using +
        map.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        });


        // sorting map
        System.out.println("\nSorting map by value");
        Map<String, Integer> sortedCities = sortByValue(map);
        map.entrySet().forEach(entry -> {
            System.out.println(entry.getKey() + ":" + entry.getValue());
        });
    }

    // java 8 sorting map by value
    public static <K, V extends Comparable<? super V>> Map<K, V>
    sortByValue(Map<K, V> map) {

        Map<K, V> result = new HashMap<>();
        Stream<Map.Entry<K, V>> st = map.entrySet().stream();


        st.sorted(Comparator.comparing(e -> e.getValue()))
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

}
