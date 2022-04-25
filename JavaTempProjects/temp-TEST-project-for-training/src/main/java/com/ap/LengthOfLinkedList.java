package com.ap;

import java.util.Iterator;
import java.util.LinkedList;

public class LengthOfLinkedList {
    public static void main(String[] args) {
        System.out.println("Test");
        LinkedList<String> test = new LinkedList<>();
        test.add("a");
        test.add("b");
        test.add("c");
        test.add("d");
        test.add("e");
        System.out.println(length(test.iterator()));

    }

    private static Integer length(Iterator<String> test) {
        if(!test.hasNext()) {
            return 0;
        } else {
            test.next();
            return 1+length(test);
        }
    }
}
