package part4StringsBitsAndOther;

import java.util.*;

public class NewMethodsInListIterationCollection {
    public static void main(String[] args) {
        System.out.println("New methods in List Iterator Collection");
        // Of course most important are stream() and parallelStream()
        // also important is spliterator()

        // New method on Iterable 'forEach()'

        // New mthod on Collection 'removeIf()'
        // example list.removeIf(e->e.length()>3)

        // New method on List 'replaceAll()'


        List<String> stringList = new ArrayList<>();
        stringList.add("one");
        stringList.add("two");
        stringList.add("three");
        stringList.add("four");

        stringList.replaceAll(s -> s.replace("o", "0"));
        System.out.println("After replacing 'o' to '0' in all elements");
        stringList.forEach(System.out::println);

        System.out.println("\nAfter removing elements with 'r'");
        stringList.removeIf(e -> e.contains("r"));
        stringList.forEach(System.out::println);

    }
}
