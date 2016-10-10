package part2StreamsAndCollectors;

import com.my.java8.Person;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

// https://docs.oracle.com/javase/8/docs/api/java/util/stream/Collectors.html
public class Collectors {
    public static void main(String[] args) {
        List<String> listToCollect = Arrays.asList("Abc", "def", "ghi", "jkl");

        String joined = listToCollect.stream().map(e->e).collect(java.util.stream.Collectors.joining(","));

        System.out.println(joined);

        Person Anna = new Person (24, "Anna");
        Person John = new Person(21, "John");
        Person Roger = new Person(24, "Roger");

        List<Person> people = Arrays.asList(Anna, John, Roger);

        //=== Simple collect ages to set

        Set<Integer> collectSet = people.stream().collect(java.util.stream.Collectors.mapping(e -> e.getAge(),
                java.util.stream.Collectors.toSet()));
        System.out.println(collectSet); // it is set, so there is no duplicates

        // === Interesgin collecting Group By
        Map<Integer, List<Person>> collectGroupBy = people.stream().
                collect(java.util.stream.Collectors.groupingBy(Person::getAge));
        System.out.println(collectGroupBy);
    }
}

// e,e -> {}