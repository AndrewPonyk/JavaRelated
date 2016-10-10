package part2StreamsAndCollectors;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// https://docs.oracle.com/javase/tutorial/collections/streams/
public class IntermediaryAndTerminalOperations {
    public static void main(String[] args) {
        System.out.println("Intermediary And Terminal Operations:");
        List<StringBuilder> numbers = Arrays.asList(new StringBuilder("one"), new StringBuilder("two"), new StringBuilder("three"), new StringBuilder("four"), new StringBuilder("five"));


        long k = numbers.stream().peek(System.out::println).count();
        System.out.println("k = " + k);

        numbers.stream().peek(System.out::println);
    }
}

// [...] -> {...} -> 5

//      Pipelines and Streams
/*
A pipeline is a sequence of aggregate operations. The following example prints the
male members contained in the collection roster with a pipeline that consists of
the aggregate operations filter and forEach:

        roster
        .stream()
        .filter(e -> e.getGender() == Person.Sex.MALE)
        .forEach(e -> System.out.println(e.getName()));
*/

//  A pipeline contains the following components:
// 1)A source: This could be a collection, an array, a generator function, or
// an I/O channel. In this example, the source is the collection roster.

    //2)Zero or more intermediate operations. An intermediate operation, such as filter, produces a new stream

    //3)A terminal operation. A terminal operation, such as forEach, produces a non-stream result,
    // such as a primitive value (like a double value), a collection, or in the case of
    // forEach, no value at all. In this example, the parameter of the forEach operation is the
    // lambda expression e -> System.out.println(e.getName()), which invokes the method getName
    // on the object e. (The Java runtime and compiler infer that the type of the object e is Person.)

