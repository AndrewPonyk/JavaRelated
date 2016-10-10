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
                range(0, countries.size()). // 0,1,2,3
                filter(n -> n % 3 == 0). //
                mapToObj(e-> countries.get(e).substring(0,2)).collect(
                Collectors.toList()
        );

        nThCountries.forEach(e-> System.out.println(e)); // every 3th

        System.out.println();
        IntStream.range(0, 4).forEach(e->{System.out.println(countries.get(e));});
    }
}
