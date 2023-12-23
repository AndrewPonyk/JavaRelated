package com.ap;

import com.google.gson.GsonBuilder;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        System.out.println( "Hello World!" );

        GsonBuilder builder = new GsonBuilder();
        System.err.println(builder.create().toJson(new App()));
        
    }
}
