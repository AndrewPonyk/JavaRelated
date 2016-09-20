package book808questions.chap3CoreJavaAPIs;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class ArrayListSomeExample {
    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        integers.add(1);
        integers.add(2);

        System.out.println(integers); // output : [1,2]


        String [] arr = {"Hello", "2"};
        System.out.println(arr); // [Ljava.lang.String;@74a14482
        // to print array use  : java.util.Arrays.toString(bugs)
        System.out.println(java.util.Arrays.toString(arr));
    }
}
