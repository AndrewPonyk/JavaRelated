package ch4;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;

public class Programch4 {
    public static void main(String[] args) {
        System.out.println("Chapter 4 Persistance lifecycle");

        example1SaveMailMessage();
        readThisRelationship();
        oneToManyExample();

        saveInnerEntity();

        SessionUtil.closeFactory();
    }

    private static void saveInnerEntity() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        PersonInner p = new PersonInner();
        p.setName("I am inner person");
        session.save(p);


        transaction.commit();
        session.close();
    }

    public static void example1SaveMailMessage() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();
        Email email1 = new Email("Subject 1");
        Messagech4 message1 = new Messagech4("Message 1");

        email1.setMessage(message1);
        message1.setEmail(email1);

        session.save(email1);
        //session.save(message1);

        transaction.commit();
        session.close();
    }

    public static void readThisRelationship() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Query query = session.createQuery("from Email e");
        Email e = (Email) query.uniqueResult();

        System.out.println("... " + e.getMessage().getContent());

        transaction.commit();
        session.close();
    }

    public static  void oneToManyExample(){
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        TestO o1 = new TestO();
        o1.name = "o";

        TestM m1 = new TestM();
        m1.name = "m1";
        m1.father = o1;

        TestM m2 = new TestM();
        m2.name = "m2";
        m2.father = o1;

        o1.manys = new HashSet<>();
        o1.manys.add(m1);
        o1.manys.add(m2);

        //session.update(o1); // The given object has a null identifier, it is transient object

        Serializable save = session.save(o1);
        //session.save(o1); // if you call twice it works , no exception no error

        transaction.commit();
        session.close();
    }


    @Entity
    public static class PersonInner{
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column
        private String name;

        public PersonInner(){

        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
