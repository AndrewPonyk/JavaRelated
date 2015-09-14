package com.my.java8;

import java.util.ArrayList;
import java.util.List;

/**
 * Hello world!
 *
 */

public class App
{
    public static void main( String[] args )
    {
        List<String> strings = new ArrayList<String>();
        strings.add("Hello");

        strings.forEach(x->System.out.println(x));

        System.out.println( "Hello World!" );
    }
}
