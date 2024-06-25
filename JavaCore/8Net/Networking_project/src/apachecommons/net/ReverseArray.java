package apachecommons.net;

import java.util.Arrays;

public class ReverseArray {

    public static void main(String[] args) {
        Integer[] arr = {1,2,3,4,5,0};

        final Integer[] reverse = reverse(arr, 0);
        System.out.println(reverse);
        Arrays.stream(reverse).forEach(System.out::println);
    }

    static Integer[] reverse(Integer[] arr, int counter) {
        if (counter < arr.length / 2) { // base case
            int temp = arr[counter];
            arr[counter] = arr[arr.length - counter - 1];
            arr[arr.length - counter - 1] = temp;
            return reverse(arr, counter+1);
        } else {
            return arr;
        }
    }
}
