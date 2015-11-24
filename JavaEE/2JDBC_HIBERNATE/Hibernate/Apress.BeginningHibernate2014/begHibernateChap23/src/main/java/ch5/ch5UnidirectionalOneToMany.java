package ch5;

import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ch5UnidirectionalOneToMany {
    public static void main(String[] args) {
        setUnidirectOneToMany();

        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        List<Onech5> list = session.createQuery("from Onech5 o").list();
        for (Onech5 item : list){
            printOnech5(item);
        }

        transaction.commit();
        session.close();

        SessionUtil.closeFactory();
    }

    public static void printOnech5(Onech5 o){
        System.out.println(o.getName());
        for(Manych5 item : o.getCollection()){
            System.out.println(item.getName());
        }
        System.out.println("----");
    }

    private static void setUnidirectOneToMany() {
        System.out.println("Unidirectional One To Many");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Onech5 one1 = new Onech5();
        one1.setName("one1");
        one1.setCollection(new HashSet<Manych5>());
        session.save(one1);

        Manych5 m1 = new Manych5();
        m1.setName("m1");
        m1.setOneId(one1.getId());
        Manych5 m2 = new Manych5();
        m2.setName("m2");
        m2.setOneId(one1.getId());
        one1.getCollection().add(m1);
        one1.getCollection().add(m2);
        session.save(one1);

        Onech5 one2 = new Onech5();
        one2.setName("one2");
        session.save(one2);

        Onech5 one3 = new Onech5();
        one3.setName("one3");
        one3.setCollection(new HashSet<Manych5>());
        session.save(one3);

        Manych5 m3 = new Manych5();m3.setOneId(one3.getId());m3.setName("Many333");
        one3.getCollection().add(m3);
        session.save(one3);


        transaction.commit();
        session.close();
    }

    @Entity(name = "Onech5")
    @Table(name = "Onech5")
    public static class Onech5{
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @OneToMany(cascade = CascadeType.ALL)
        @JoinColumn(name = "oneId")
        private Set<Manych5> collection;

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

        public Set<Manych5> getCollection() {
            return collection;
        }

        public void setCollection(Set<Manych5> collection) {
            this.collection = collection;
        }
    }

    @Entity(name = "Manych5")
    @Table(name = "Manych5")
    public static class Manych5{
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;
        private String name;
        private Long oneId;

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

        public Long getOneId() {
            return oneId;
        }

        public void setOneId(Long oneId) {
            this.oneId = oneId;
        }
    }
}
