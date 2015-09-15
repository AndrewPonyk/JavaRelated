package com.my.java8;

import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */

public class App
{
    public static void someAction(String s){
        System.out.println(s.charAt(0));
    }

    public static void main( String[] args )
    {
        List<String> strings = new ArrayList<String>();
        strings.add("Hello");
        strings.add("Epa");

        strings.forEach(System.out::println);
        strings.forEach(App::someAction);

        System.out.println( "Hello World!" );
    }
}
