package app;

import java.util.ArrayList;
import java.util.List;

public class UserDirectoryException extends Throwable {


    public static void main(String[] args) {
        List<String> test = new ArrayList<>();
        test.add("1");
        test.add("2");


        System.out.println(test);
        test.add(0, "a");
        System.out.println(test);
    }
}
