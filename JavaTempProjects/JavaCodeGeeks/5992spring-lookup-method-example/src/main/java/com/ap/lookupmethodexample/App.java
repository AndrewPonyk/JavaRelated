package com.ap.lookupmethodexample;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.Closeable;
import java.io.IOException;

// lookup method is used for
// used for dynamically overriding a class and its abstract methods to create instances


// lookup method with annotations
// http://stackoverflow.com/questions/3891997/how-to-do-spring-lookup-method-injection-with-annotations
public class App
{
    public static void main( String[] args ) throws IOException {
        ApplicationContext context = new ClassPathXmlApplicationContext("methodlookupbeans.xml");

        //PizzaShopAbstract can be abstract of interface, Srping will create subclass
        PizzaShopAbstract pizzaShop = context.getBean("pizzaShop", PizzaShopAbstract.class);
        System.out.println(pizzaShop.getPizza());

        ((Closeable)context).close();

    }
}
