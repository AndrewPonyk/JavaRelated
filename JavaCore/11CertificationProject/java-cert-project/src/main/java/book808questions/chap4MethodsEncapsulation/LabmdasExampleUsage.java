package book808questions.chap4MethodsEncapsulation;

import java.util.function.Predicate;

/**
 * Created by 4G-PC on 20.09.2016.
 */
public class LabmdasExampleUsage {
    //Which of the following lines can be inserted at line 11 to print true? (Choose all that
    //apply)
    public static void main(String[] args) {
        System.out.println(test(i -> i == 5));
        //System.out.println(test(i -> {i == 5;})); // missed return
        System.out.println(test((i) -> i == 5));
        //System.out.println(test((int i) -> i == 5); // NO Autoboxing
        //System.out.println(test((int i) -> {return i == 5;})); // NO Autoboxing
        System.out.println(test((i) -> {return i == 5;}));
    }

    private static boolean test(Predicate<Integer> p) {
        return p.test(5);
    }


    // !!! Autoboxing works for collections not inferring predicates
}
