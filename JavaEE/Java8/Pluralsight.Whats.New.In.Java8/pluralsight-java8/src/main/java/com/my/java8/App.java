package com.my.java8;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class App
{
    public static void someAction(String s){
        System.out.println(s.charAt(0));
    }

    //==== New in java 8 : 
    // Lambdas
    // Default methods
    // Streams
    // Optional
    // Time Date API
    // Nashorn
    // No More Permanent Generation
    
    public static void main( String[] args )
    {
        List<String> strings = new ArrayList<String>();
        strings.add("Hello");
        strings.add("Epa");

        strings.forEach(System.out::println);
        strings.forEach(App::someAction);

        System.out.println("Hello World!");
        
        // =============================================== Stream API !!!
        
        Map<String, String> persons = new HashMap<>();
        persons.put("1003", "John Smith");
        persons.put("1421", "Ian Mack");
        
        long containsiLetter = persons.values().stream().map(e->e.contains("i")).filter(e->e).count();        
        
        System.out.println("Count of persons with 'i' letter " + containsiLetter);


        // RSA KEY , shuft '4' , public key {13, 33} , private {17, 33}
        BigInteger big = new BigInteger("4");
        big = big.pow(17).mod(new BigInteger("33"));

        BigInteger rozshufr = big.pow(13).mod(new BigInteger("33"));
        System.out.println(rozshufr.toString());

    }
    
    
}
