package book808questions.preassesmenttest;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by 4G-PC on 11.09.2016.
 */
public class question7 {
    public static void main(String[] args) {
        int[] array = {6,9,8};
        List<Integer> list = new ArrayList<>();
        list.add(array[0]);
        list.add(array[2]);
        list.set(1, array[1]);
        list.remove(0);
        System.out.println(list);
    }
}
















//Answer :
//[9]
