package ch5;

import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

public class ch5EmbeddableComposeIdProgram {
    public static void main(String[] args) {
        System.out.println("Id in Hibernate can be composed");

        saveEntityWithComposedId();
        getEntitiesWithComposedId();

        SessionUtil.closeFactory();
    }

    public static void saveEntityWithComposedId(){
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        ISBN isbn1 = new ISBN();
        isbn1.setGroup(10);
        isbn1.setCheckdigit(1234);
        isbn1.setPublisher(1);
        isbn1.setTitle(2014);
        CPKBook book1 = new CPKBook();
        book1.setId(isbn1);
        book1.setName("Name 1");
        session.save(book1);

        ISBN isbn2 = new ISBN();
        isbn2.setGroup(20);
        isbn2.setCheckdigit(278);
        isbn2.setPublisher(2);
        isbn2.setTitle(2015);
        CPKBook book2 = new CPKBook();
        book2.setId(isbn2);
        book2.setName("Name 2");
        session.save(book2);

        ISBN isbn3 = new ISBN();
        isbn3.setGroup(30);
        isbn3.setCheckdigit(278);
        isbn3.setPublisher(2);
        isbn3.setTitle(2015);
        CPKBook book3 = new CPKBook();
        book3.setId(isbn3);
        book3.setName("Name 3");
        session.save(book3);

        transaction.commit();
        session.close();
    }

    public static void getEntitiesWithComposedId(){
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        List<Integer> list = session.createQuery("select c.id.publisher from CPKBook c").list();

        System.out.println("Ids of Publishers Of books");
        for (Integer item : list){
            System.out.println(item);
        }
        System.out.println("============================");

        transaction.commit();
        session.close();
    }

    @Entity(name = "CPKBook")
    @Table(name = "CPKBook")
    public static class CPKBook{
        @Id
        private ISBN id;

        private String name;

        public ISBN getId() {
            return id;
        }

        public void setId(ISBN id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Embeddable
    public static class ISBN implements Serializable{
        @Column(name="group_number")
        private // because "group" is an invalid column name for SQL
        int group;
        private int publisher;
        private int title;
        private int checkdigit;

        public int getGroup() {
            return group;
        }

        public void setGroup(int group) {
            this.group = group;
        }

        public int getPublisher() {
            return publisher;
        }

        public void setPublisher(int publisher) {
            this.publisher = publisher;
        }

        public int getTitle() {
            return title;
        }

        public void setTitle(int title) {
            this.title = title;
        }

        public int getCheckdigit() {
            return checkdigit;
        }

        public void setCheckdigit(int checkdigit) {
            this.checkdigit = checkdigit;
        }
    }
}
