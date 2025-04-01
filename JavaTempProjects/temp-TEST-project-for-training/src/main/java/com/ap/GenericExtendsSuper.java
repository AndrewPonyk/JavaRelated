package com.ap;

import java.util.ArrayList;
import java.util.List;

public class GenericExtendsSuper {
    public static void main(String[] args) {
        ArrayList<? extends Number> test = new ArrayList<>();

        List<Number> numbers1 = new ArrayList<>();
        List<Integer> integers1 = new ArrayList<>();
        integers1.add(1);
        integers1.add(2);
        addAll(numbers1, integers1); // Adds integers to a list of numbers
        System.out.println("Example 1: " + numbers1); // Output: [1, 2]
    }

    public static <T extends Number> void addAll(List<? super T> to, List<T> from) {
        for (T n : from) {
            to.add(n);
        }
    }
}
