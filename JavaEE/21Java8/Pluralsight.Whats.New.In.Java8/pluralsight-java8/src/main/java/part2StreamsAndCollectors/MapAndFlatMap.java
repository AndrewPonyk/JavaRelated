package part2StreamsAndCollectors;

import java.util.Arrays;
import java.util.List;


public class MapAndFlatMap {
    public static void main(String[] args) {
        System.out.println("Map and FlatMap");

        List<Integer> numb3rs = Arrays.asList(1, 2, 3, 4, 5);
        List<Integer> numb3rs2 = Arrays.asList(22, 33, 44, 55, 66);

        List<List<Integer>> twoLevelList =
                Arrays.asList(numb3rs, numb3rs2);

        twoLevelList.stream().flatMap(l -> l.stream()).map(e -> e + 10).forEach(System.out::println);

        /*
        * To do this pre-Java 8 you just need a loops:

        List<List<Integer>> integerLists = Arrays.asList(
          Arrays.asList(1, 2),
          Arrays.asList(3, 4),
          Arrays.asList(5)
        )

        List<Integer> flattened = new ArrayList<>();

        for (List<Integer> integerList : integerLists)
        {
          flattened.addAll(integerList);
        }

        for (Integer i : flattened)
        {
          System.out.println(i);
        }

        * */
    }
}
