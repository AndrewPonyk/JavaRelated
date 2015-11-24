package ch5.inheritanceExamples;

import ch7.JPASessionUtil;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class ch5HibernateInheritanceProgram {
    public static void main(String[] args) {
        System.out.println("Hibernate Inheritance example : ");

        saveDifferentBooks();
        JPASessionUtil.closeAllEntityManagerFactories();
    }

    private static void saveDifferentBooks() {
        System.out.println("Create and save books");
        EntityManager em = JPASessionUtil.getEntityManager("utiljpa");
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        PaperBook p1 = new PaperBook();
        p1.setName("Paper 1 ");
        p1.setAuthor("AUthor 1");
        p1.setWeight(2.01);
        p1.setPrice(100.0);


        EBook e1 = new EBook();
        e1.setName("e1");
        e1.setFormat("pdf");
        e1.setAuthor("e1 author");
        e1.setPrice(100.0);

        em.persist(p1);
        em.persist(e1);

        transaction.commit();
        em.close();
    }
}
