package com.ap;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.io.Serializable;

/**
 * Created by andrii on 11.06.17.
 */
//https://examples.javacodegeeks.com/enterprise-java/hibernate/hibernate-first-level-cache-example/

public class HibernateFirstLevelCacheMain {
    public static void main(String[] args) {
        System.out.println("Test Hibernate first level cache");
        Integer ID_OBJECT = 27;

        SessionFactory sessionFactory = HibernateSessionFactory
                .getSessionFactory();

        Session session = sessionFactory.openSession();
        session.beginTransaction();
        session.save(new Department(ID_OBJECT, "Malaga"));
        session.getTransaction().commit();

        // hibernate does not call SELECT from db
        Department loaded = (Department) session.get(Department.class,
                ID_OBJECT);
        System.out.println("The Department name is: " + loaded.getName());
        loaded.setName("Madrid");
        // here also, Select is not executed
        loaded = (Department) session.get(Department.class, ID_OBJECT);
        System.out.println("The Department name is: " + loaded.getName());
        session.close();

        session = sessionFactory.openSession();
        //select from db is executed, as this is new session
        loaded = (Department) session.get(Department.class, ID_OBJECT);
        System.out.println("The Department name is: " + loaded.getName());
        session.close();
        sessionFactory.close();
    }
}


