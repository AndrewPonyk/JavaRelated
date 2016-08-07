package part2StreamsAndCollectors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.*;
import java.util.stream.Collectors;

public class NTHElementFromCOLLECTIONEXAMPLE {
    public static void main(String[] args) {
        System.out.println("Get N-th element from collection");

        List<String> countries = Arrays.asList("Ukraine", "Georgia", "Sweden",
                "Slovakia", "Chech", "Spain");

        List<String> nThCountries = IntStream.
                range(0, countries.size()).
                filter(n -> n % 3 == 0).
                mapToObj(countries::get).collect(
                Collectors.toList()
        );

        nThCountries.forEach(System.out::println); // every 3th

        System.out.println();
        IntStream.range(0, 4).forEach(e->{System.out.println(countries.get(e));});
    }
}
