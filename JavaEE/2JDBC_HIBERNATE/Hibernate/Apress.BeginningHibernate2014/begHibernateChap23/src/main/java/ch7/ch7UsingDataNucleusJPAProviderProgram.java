package ch7;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class ch7UsingDataNucleusJPAProviderProgram {
    public static void main(String[] args) {
        System.out.println("Using Data Nucleus : http://www.datanucleus.org/");
    //org.datanucleus.api.jpa.PersistenceProviderImpl
        EntityManager em = JPASessionUtil.getEntityManager("dataNucleusUnit");

        EntityTransaction transaction = em.getTransaction();
        transaction.begin();
        Thing t1 = new Thing();
        t1.setName("From datanucleus JPA WITH DN");
        em.persist(t1);
        transaction.commit();

        em.close();
        JPASessionUtil.closeAllEntityManagerFactories();
    }
}

// !!!! When using Data nucleus you need first Enhance your entity,
// in maven you can do it by mvn datanucleus:enhance (include plugin in pom.xml)
// and after this call
// mvn clean package exec:java -Dexec.mainClass="ch7.ch7UsingDataNucleusJPAProviderProgram"