package ch5;

import ch3.Person;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

public class NamedQueriesExampleProgram {
    public static void main(String[] args) {
        System.out.println("Named query example");

        savePersons();
        useNamedQueries();
        SessionUtil.closeFactory();
    }

    public static void savePersons(){
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Person p1 = new Person();
        p1.setName("Andrii");

        Person p2 = new Person();
        p2.setName("Ivan");

        session.save(p1);
        session.save(p2);

        transaction.commit();
        session.close();

    }

    public static void useNamedQueries(){
        Session session = SessionUtil.getSession();

        //Entity Person has two named queries

        //Using first named query from person entity
        Query selectAuthorByName = session.getNamedQuery("selectAuthorByName");
        selectAuthorByName.setParameter("name", "Andrii");
        Person person = (Person) selectAuthorByName.uniqueResult();
        System.out.println(person.getId() + "  : " + person.getName());

        // second named query
        Query selectAuthorByNameAndId = session.getNamedQuery("selectAuthorByNameAndId");
        selectAuthorByNameAndId.setParameter("id", 2L);
        selectAuthorByNameAndId.setParameter("name", "Ivan");
        Person p2 = (Person) selectAuthorByNameAndId.uniqueResult();
        System.out.println(p2.getName());
        session.close();

    }

}
