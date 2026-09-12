package org.computationalgeometry.util;

import java.util.List;

public final class ConsoleFormatter {
    private ConsoleFormatter() {
    }

    public static <T> void printList(String title, List<T> values) {
        System.out.println(title + " (" + values.size() + ")");
        for (int i = 0; i < values.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + values.get(i));
        }
    }
}
