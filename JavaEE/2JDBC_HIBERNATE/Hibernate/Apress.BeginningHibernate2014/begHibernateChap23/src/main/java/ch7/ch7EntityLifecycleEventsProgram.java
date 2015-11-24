package ch7;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class ch7EntityLifecycleEventsProgram {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Entity Lifecycle events Example");
        /*EntityManagerFactory emf = Persistence.createEntityManagerFactory("utiljpa");
        EntityManager em = emf.createEntityManager();*/

        EntityManager em = JPASessionUtil.getEntityManager("utiljpa");

        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        Thing t1 = new Thing();
        t1.setName("abc");
        em.persist(t1);
        System.out.println(em.getClass());

        transaction.commit();
        em.close();
        JPASessionUtil.closeAllEntityManagerFactories();
        //emf.close();

    }


}
