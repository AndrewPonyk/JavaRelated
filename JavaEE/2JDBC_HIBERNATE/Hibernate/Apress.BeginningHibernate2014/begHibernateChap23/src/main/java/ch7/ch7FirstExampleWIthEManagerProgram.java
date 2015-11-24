package ch7;


import ch3.Person;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

public class ch7FirstExampleWIthEManagerProgram {
    public static void main(String[] args) {
        System.out.println("Using ENtity Manager");
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("utiljpa");
        EntityManager em = emf.createEntityManager();

        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        Person p1 = new Person();
        p1.setName("From em");
        em.persist(p1);

        transaction.commit();

        System.out.println(em.getClass());
        em.close();
        emf.close();
    }
}
