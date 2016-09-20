package book808questions.chap3CoreJavaAPIs;

import java.util.Arrays;
import java.util.List;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class InterestingConversionFromListToArray {
    public static void main(String[] args) {
        String[] array = { "hawk", "robin" }; // [hawk, robin]

        List<String> list = Arrays.asList(array); // returns fixed size list
        // returns class java.util.Arrays$ArrayList

        System.out.println(list.size()); // 2

        list.set(1, "test"); // [hawk, test]

        array[0] = "new"; // [new, test]

        for (String b : array) System.out.print(b + " "); // new test

        list.remove(1); // throws UnsupportedOperation Exception

    }
}
