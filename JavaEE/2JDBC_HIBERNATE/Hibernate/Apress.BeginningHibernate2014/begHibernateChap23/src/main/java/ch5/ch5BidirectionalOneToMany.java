package ch5;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ch5BidirectionalOneToMany {
    public static void main(String[] args) {
        System.out.println("Bidirectional One to many");
        saveOneToMany();
        readOneToMany();

        removeOneBookFromApress();
        readOneToMany();
        SessionUtil.closeFactory();
    }

    private static void removeOneBookFromApress() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Publisher apress = (Publisher) session.createQuery("from ch5Publisher p where p.name='Apress'").uniqueResult();
        Iterator<Book> iterator = apress.getBooks().iterator();
        while (iterator.hasNext()){
            Book next = iterator.next();
            if(next.getName().toLowerCase().contains("hibernate")){
                iterator.remove();
            }
        }

        session.saveOrUpdate(apress);
        transaction.commit();

    }

    private static void readOneToMany() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        List<Publisher> list = session.createQuery("from ch5Publisher p").list();
        for (Publisher p : list){
            System.out.println("Publisher ::: " + p.getName());
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for(Book book : p.getBooks()){
                System.out.println(book.getName());
            }
        }
        System.out.println("===========");
        transaction.commit();
    }

    private static void saveOneToMany() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Publisher apress = new Publisher();
        apress.setName("Apress");

        Book hibernate = new Book();
        hibernate.setName("Hibernate 2014");
        apress.addBook(hibernate);
        Book spring = new Book();
        spring.setName("Spring 2015");
        apress.addBook(spring);

        //
        Publisher wrox = new Publisher();
        wrox.setName("Wrox");
        Book springWrox2015 = new Book();
        springWrox2015.setName("Spring wrox 2015");
        wrox.addBook(springWrox2015);

        session.save(apress);
        session.save(wrox);
        transaction.commit();
    }

    @Entity(name = "ch5Publisher")
    @Table(name = "ch5Publisher")
    public static class Publisher{
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @OneToMany(mappedBy = "publisher", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
        private Set<Book> books = new HashSet<>();

        public void addBook(Book book){
            this.books.add(book);
            book.setPublisher(this);
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Set<Book> getBooks() {
            return books;
        }

        public void setBooks(Set<Book> books) {
            this.books = books;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @Table(name = "ch5Book")
    @Entity(name = "ch5Book")
    public static class Book{
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Long id;

        private String name;

        @ManyToOne
        @PrimaryKeyJoinColumn
        private Publisher publisher;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Publisher getPublisher() {
            return publisher;
        }

        public void setPublisher(Publisher publisher) {
            this.publisher = publisher;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}