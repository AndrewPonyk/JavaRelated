package com.mprog;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Test {

    public static void main(String[] args) {

        List<String> strings = List.of("88", "11", "22", "33", "44", "55", "66");

        Function<String, String> concatinate = str -> str + str;

        Function<String, Integer> mapToInt = Integer::parseInt;

        Predicate<Integer> filter = value -> value % 2 == 0;

        Consumer<Integer> sout = value -> System.out.println(value);

//       strings
//                .stream()
////                11, 22, 33
//                .map(concatinate)
////                1111, 2222,
//                .map(mapToInt)
////                1111, 2222,
//                .filter(filter)
//                        .forEach(sout);

        strings.stream()
                .map(str -> str + str)
                .map(Integer::parseInt)
                .filter(value -> value % 2 == 0)
//                8888 2222 4444 6666
                .sorted()
//                22 44 66 88
                .skip(1)
//                44 66 88
                .limit(2)
//                44 66
                .forEach(System.out::println);

//        System.out.println(integers);
    }


}
