package com.ap.lookupmethodexample;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.Closeable;
import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws IOException {
        ApplicationContext context = new ClassPathXmlApplicationContext("methodlookupbeans.xml");

        PizzaShopAbstract pizzaShop = context.getBean("pizzaShop", PizzaShopAbstract.class);
        System.out.println(pizzaShop.getPizza());

        ((Closeable)context).close();

    }
}
