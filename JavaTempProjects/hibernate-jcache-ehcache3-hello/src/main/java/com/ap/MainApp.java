package com.ap;

import org.hibernate.Session;
import org.hibernate.Transaction;

public class MainApp {
    public static void main(String[] args) throws InterruptedException {
        Session session = null;
        Transaction transaction = null;
        //Retrieve the person object from database --------------------
        session = HibernateUtil.getSessionFactory().openSession();
        transaction = session.beginTransaction();

        Person person = session.get(Person.class, 1L);
        System.out.println(person);
        transaction.commit();
        session.close();

        Thread.sleep(1000);

        //Retrieve the person object from CACHE
        session = HibernateUtil.getSessionFactory().openSession();
        transaction = session.beginTransaction();
        Person person2 = session.get(Person.class, 1L);
        System.out.println(person2);
        transaction.commit();
        session.close();

        HibernateUtil.shutdown();
    }
}

