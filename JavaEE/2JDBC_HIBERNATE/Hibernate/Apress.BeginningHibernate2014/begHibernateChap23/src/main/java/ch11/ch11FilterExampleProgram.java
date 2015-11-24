package ch11;

import org.hibernate.Session;
import org.hibernate.Transaction;
import util.SessionUtil;

import java.util.List;

public class ch11FilterExampleProgram {
    public static void main(String[] args) {
        System.out.println("Filter example : ");


        saveUsers();
        filtersExample();

        SessionUtil.closeFactory();
    }

    private static void filtersExample() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();


        session.enableFilter("endsWith1");

        List<UserWithFilter> usersEndsWith1 = session.createQuery("from UserWithFilter").list();
        for(UserWithFilter item : usersEndsWith1){
            System.out.println(item.getName() + " " + item.getId());
        }

        System.out.println("===============");

        session.disableFilter("endsWith1");
        session.enableFilter("endsWithParam").setParameter("para", "u3");
        usersEndsWith1 = session.createQuery("from UserWithFilter").list();
        for(UserWithFilter item : usersEndsWith1){
            System.out.println(item.getName() + " " + item.getId());
        }

        transaction.commit();
        session.close();
    }

    private static void saveUsers() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        UserWithFilter u1 = new UserWithFilter();
        u1.setName("u1");u1.setActive(true);

        UserWithFilter u2 = new UserWithFilter();
        u2.setName("u2");u2.setActive(true);

        UserWithFilter u3 = new UserWithFilter();
        u3.setName("u3");u3.setActive(true);

        session.save(u1);session.save(u2);session.save(u3);

        transaction.commit();
        session.close();
    }


}
