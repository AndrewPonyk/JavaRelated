package part4StringsBitsAndOther;

import java.util.*;

public class ComparatorsInJava8 {
    public static void main(String[] args) {
        System.out.println("Comparator API in Java 8");

        // sorting list, also we assume that can be nulls inside
        // comparing length of string
        // and if length if equal compare count of letter 'x' inside strings
        Comparator<String> comp = Comparator.nullsFirst(Comparator.comparing(String::length).thenComparing((e1,e2)->{


            long xCount1 = e1.chars().filter(e-> e== 'x').count();
            long xCount2 = e2.chars().filter(e-> e== 'x').count();
            return (int)(xCount1 - xCount2);
        }));

        List<String> strings = Arrays.asList("xxx", "one", "twelve", "three", "six", null);
        strings.sort(comp);
        strings.forEach(System.out::println);

        // sorting map
        Map<String, Integer> cities = new HashMap<>();


    }
}
