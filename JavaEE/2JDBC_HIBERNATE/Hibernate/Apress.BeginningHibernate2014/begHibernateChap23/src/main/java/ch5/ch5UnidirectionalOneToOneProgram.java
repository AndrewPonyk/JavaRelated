package ch5;

import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import javax.persistence.*;
import java.util.List;

// YOU SHOULD MANUALLY ADD ID to CHILD ENTITIES IN ONE TO ONE , HIBERNATE CANT SET IT FOR YOUU
// LIKE IN BIDIRECRIONAL RELATIONSHIP YOU SHOULD SET BOTH DIRECTION RELATIONSHIPS !!!!!!!!!!!!!!

public class ch5UnidirectionalOneToOneProgram {

    public static void main(String[] args) {
        System.out.println(" Unidirectional one to one. (Entities are Inner Classes) ");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        // SAVE
        Author jackLondon = new Author();
        jackLondon.setName("Jack London");
        session.persist(jackLondon);

        Biography jackBio = new Biography();
        jackBio.setInformation("Jack London was a 19th century American author and journalist, best known for the adventure novels White Fang and The Call of the Wild.");
        jackBio.setAuthorId(jackLondon.getId());
        jackLondon.setBiography(jackBio);
        session.save(jackLondon);


        // SAVE SECOND
        Author test1 = new Author();
        test1.setName("test1");
        session.save(test1);


        // SAVE THIRD
        Author test2 = new Author();
        test2.setName("test2");
        session.save(test2);

        Biography bio2 = new Biography();
        bio2.setInformation("Bio for test2");
        bio2.setAuthorId(test2.getId());
        test2.setBiography(bio2);
        session.save(test2);

        transaction.commit();
        session.close();
        readAllAuthors();

        SessionUtil.closeFactory();
    }

    public static void readAllAuthors (){
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        // READING
        List<Author> authors = (List<Author>) session.createQuery("from Author a").list();
        for(Author item : authors){
            System.out.println(item.getId() + " " + item.getName());
            if(item.getBiography() != null){
                System.out.println(item.getBiography().getInformation());
            }
            System.out.println("---------");
        }
        transaction.commit();
        session.close();
    }

    @Entity(name = "Biography")
    public static class Biography {
        @Id
        private Integer authorId;

        private String information;

        public Integer getAuthorId() {
            return authorId;
        }

        public void setAuthorId(Integer authorId) {
            this.authorId = authorId;
        }

        public String getInformation() {
            return information;
        }

        public void setInformation(String information) {
            this.information = information;
        }
    }

    @Entity(name = "Author")
    public static class Author {
        @Id
        @GeneratedValue(strategy = GenerationType.AUTO)
        private Integer id;

        private String name;

        @OneToOne(cascade=CascadeType.ALL)
        @PrimaryKeyJoinColumn
        private Biography biography;


        public Biography getBiography() {
            return biography;
        }

        public void setBiography(Biography biography) {
            this.biography = biography;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
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