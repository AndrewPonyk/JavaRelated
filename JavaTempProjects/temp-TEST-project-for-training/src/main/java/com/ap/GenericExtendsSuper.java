package com.ap;

import java.util.ArrayList;
import java.util.List;

public class GenericExtendsSuper {
    public static void main(String[] args) {
        ArrayList<? extends Number> test = new ArrayList<>();

        test.add();
    }

    public <T> void addAll(List<T extends Number> to, List<T extends Number> from) {
        for (T n: from) {
            to.add(n);
        }
    }
}
