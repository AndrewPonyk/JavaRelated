package ch8;

import ch7.JPASessionUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;
import util.SessionUtil;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;


public class ch8ExampleCachingProgram {
    public static void main(String[] args) {
        System.out.println("Cache in hibernate");

        testL1CacheUsingGet();

        SessionUtil.closeFactory();
    }

    private static void testL1CacheUsingGet() {
        Session session = SessionUtil.getSession();

        /* insert record with id=10 to db :  insert into CacheEntity values (10, 'abc');*/
        try {
            System.in.read();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Transaction transaction = session.beginTransaction();

        CacheEntity c1 = new CacheEntity();
        c1.setName("c1");
        session.save(c1);

        CacheEntity ec = (CacheEntity) session.load(CacheEntity.class, 10);
        System.out.println(ec.getName());

        session.clear(); // uncomment this to clear L1 cache

        ec = (CacheEntity) session.load(CacheEntity.class, 10);
        System.out.println(ec.getName());

        session.doWork(new Work() {
            @Override
            public void execute(Connection connection) throws SQLException {
                System.out.println(connection.getTransactionIsolation());
            }
        });

        transaction.commit();
        session.close();

    }

}