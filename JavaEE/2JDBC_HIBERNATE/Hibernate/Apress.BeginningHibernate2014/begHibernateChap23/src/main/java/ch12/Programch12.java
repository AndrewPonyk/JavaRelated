package ch12;


import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.List;

// Hibernate OGM DOESNT WORK WITH INNER CLASSES (Because of $ sign ), so if you have any inner entity you
// SHOULD give @Entity(name="NameWithoutDolarSign")

// good tutorial
// http://blog.eisele.net/2015/01/nosql-with-hibernate-ogm-part-one.html
public class Programch12 {
    static EntityManagerFactory factory = null;

    public static synchronized EntityManager getEntityManager() {
        if (factory == null) {
            factory = Persistence.createEntityManagerFactory("chapter12-mongo");
        }
        return factory.createEntityManager();
    }

    public static void main(String[] args) {

        System.out.println("Hibernate and NOSQL");
        saveEntity();
        findAll();
        //getEntityById(1L);
    }

    public static void saveEntity(){
        EntityManager em = getEntityManager();
        em.getTransaction().begin();
        PersonNoSQL a = new PersonNoSQL();
        a.setName("Andrew");

        em.persist(a);
        em.getTransaction().commit();
        em.close();
    }

    public  static void getEntityById(long id){
        EntityManager em = getEntityManager();
        em.getTransaction().begin();

        System.out.println(em.find(PersonNoSQL.class, id).getName());

        em.getTransaction().commit();
        em.close();
    }

    public  static void findAll(){
        EntityManager em = getEntityManager();
        em.getTransaction().begin();

        List<PersonNoSQL> resultList = em.createQuery("from  PersonNoSQL").getResultList();
        for(PersonNoSQL item : resultList){
            System.out.println(item.getId() + ":" + item.getName());
        }
        em.getTransaction().commit();
        em.close();
    }
}