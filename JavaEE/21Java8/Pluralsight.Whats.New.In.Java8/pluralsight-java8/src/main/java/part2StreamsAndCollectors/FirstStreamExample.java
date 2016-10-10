package part2StreamsAndCollectors;


import java.util.*;

public class FirstStreamExample {

    public static  void main(String []arg){
        System.out.println("First STREAM example");

        List<String> cities = Arrays.asList("Lviv", "Poltava", "Kyiv", "Odesa");

        cities.replaceAll(e -> e.replaceAll("v", "V"));// great thing
        cities.forEach(System.out::println);

        // join String collection
        System.out.println(cities.stream().reduce((a,b)-> a+b).get() + " <- reduce");

        // sum of length
        System.out.println(cities.stream().map(e->e.length()).reduce((a,b)->a+b).get());


        // ------------------- Streams and Maps
        Map<String, Integer> citiesPopulation = new HashMap<>();
        citiesPopulation.put("Lviv", 800000);
        citiesPopulation.put("Kyiv", 2500000);
        citiesPopulation.put("Odesa",1010000);


        citiesPopulation.forEach((a, b) -> {
            System.out.println(a + " " + b);
        });

        citiesPopulation.entrySet().stream().forEach(e->{
            System.out.println(e.getKey()+e.getValue());
        });

        //=== count summary population of cities
        // if there is no elements in map we'll get 0
        Integer sum = citiesPopulation.entrySet().stream().map(e -> e.getValue()).reduce(Integer::sum).
                orElse(0);
        System.out.println("Summary population : " + sum);
    }
}