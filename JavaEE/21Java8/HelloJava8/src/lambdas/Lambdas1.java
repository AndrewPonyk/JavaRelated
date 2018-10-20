package lambdas;

import fucntionalinterfaces.ITrade;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Created by andrew on 27.06.15.
 */
public class Lambdas1 {

    public static void main(String[] arg){
        System.out.println("s ");
        Arrays.asList(new Integer[]{1, 6, 2, 4}).forEach(x -> System.out.println(x.intValue()));
        List<Integer> integers = Arrays.asList(new Integer[]{1, 5, -5, 0, 2, 4});

        integers.sort((x, y) -> {
            return y - x;
        });

        integers.forEach(x -> System.out.println("x = " + x));

        Runnable r;

        ITrade itradeChecker = (String s) -> s.length()>3;

        someMethod(itradeChecker);
        someMethod(s->null);


        Optional<Integer> testOptional = Optional.empty();

        final Integer integer = testOptional.orElseGet(() -> new Integer(100));
        System.out.println(integer);
    }


    public static void someMethod(ITrade t){
        System.out.println(t.check("d344"));
    }
}