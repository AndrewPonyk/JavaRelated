package com.my;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) {
        SessionFactory sf;
        HibernateProxy p = new HibernateProxy() {
            public Object writeReplace() {
                return null;
            }

            public LazyInitializer getHibernateLazyInitializer() {
                return null;
            }
        };
        System.out.println("Hello World!");
    }
}
