package book808questions.chap4MethodsEncapsulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 4G-PC on 20.09.2016.
 */
public class ArrayListRemoveIfExample {
    public static void main(String[] args) {
        List<Integer> integers = new ArrayList<>();
        integers.add(10);
        integers.add(100);

        integers.removeIf(i->i>10);
        System.out.println(integers);
    }
}
