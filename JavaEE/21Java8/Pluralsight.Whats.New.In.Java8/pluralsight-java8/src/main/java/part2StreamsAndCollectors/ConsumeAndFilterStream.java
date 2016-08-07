package part2StreamsAndCollectors;

import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConsumeAndFilterStream {
    public static void main(String[] args) {
        System.out.println("Consume and Filter Stream");


        Stream<String> stream = Stream.of("one", "two", "three", "four", "five");

        Predicate<String> p1 = e -> e.length() > 3;
        Predicate<String> p2 = e -> e.startsWith("t");


        stream.filter(p1.or(p2)).
               forEach(System.out::println);
    }
}
