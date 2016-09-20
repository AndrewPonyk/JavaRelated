package book808questions.preassesmenttest;

import java.util.function.Predicate;

/**
 * Created by 4G-PC on 17.09.2016.
 */
public class Question17Lambdas {
     public static void main(String[] args) {
         // INSERT CODE HERE
          System.out.println(test(i -> i == 5));
          //System.out.println(test(i -> {i == 5;}));
          System.out.println(test((i) -> i == 5));
          //System.out.println(test((int i) -> i == 5);
          //System.out.println(test((int i) -> {return i == 5;}));
          System.out.println(test((i) -> {return i == 5;}));
         }
     private static boolean test(Predicate<Integer> p) {
         return p.test(5);
         }
}
