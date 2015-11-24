package ch8;
import ch7.JPASessionUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jdbc.Work;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

// Table 8-2. JDBC Isolation Levels
/*
        0 None Anything is permitted; the database or driver does not support transactions.
        1 Read Uncommitted Dirty, nonrepeatable, and phantom reads are permitted.
        2 Read Committed Nonrepeatable reads and phantom reads are permitted.
        4 Repeatable Read Phantom reads are permitted.
        8 Serializable The rule must be obeyed absolutely.
*/

/*
        -A dirty read may see the in-progress changes of an uncommitted transaction. As with the isolation example
        discussed in the preceding sidebar, it could see the wrong ZIP code for an address.
        - A nonrepeatable read sees different data for the same query. For example, it might determine a specific user’s ZIP
        code at the beginning of the transaction and again at the end, and get a different answer both times without making
        any updates.
        - A phantom read sees different numbers of rows for the same query. For example, it might see 100 users in the
        database at the beginning of the query and 105 at the end without making any updates.
*/

public class ch8TransactionLevelsExampleProgram {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Transaction isolation levels example : ");

        Thread t1 = new Thread(new Runnable() {
            @Override
            public void run() {
                firstTransaction();
            }
        });

        Thread t2 = new Thread(new Runnable() {
            @Override
            public void run() {
                secondTransaction();
            }
        });

        t1.start();
        t2.start();

        t1.join();
        t2.join();
        JPASessionUtil.closeAllEntityManagerFactories();
    }

    public static void firstTransaction(){
        Session session = JPASessionUtil.getSession("utiljpa");
        session.doWork(
                new Work() {
                    public void execute(Connection connection) throws SQLException {
                        connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    }
                }
        );
        try {Thread.sleep(1000);} catch (InterruptedException e) {e.printStackTrace();}
        Transaction transaction = session.beginTransaction();
        System.out.println("First transaction start ");

        TransactionEntity andrew = new TransactionEntity();
        andrew.setName("Andrew");
        andrew.setAge(23);

        TransactionEntity andrew18 = new TransactionEntity();
        andrew18.setName("Andrew");
        andrew18.setAge(18);

        TransactionEntity andrew10 = new TransactionEntity();
        andrew10.setName("Andrew10");
        andrew10.setAge(10);

        try {Thread.sleep(2000);} catch (InterruptedException e) {e.printStackTrace();}

        session.save(andrew);
        session.save(andrew18);
        session.save(andrew10);
        transaction.commit();
        session.close();
        System.out.println("First transaction commit");
    }

    public static void secondTransaction(){
        Session session = JPASessionUtil.getSession("utiljpa");

        session.doWork(
                new Work() {
                    public void execute(Connection connection) throws SQLException {
                        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    }
                }
        );

        Transaction transaction = session.beginTransaction();

        System.out.println("Second transaction start" );
        List<TransactionEntity> people0 = session.createQuery("from TransactionEntity p where p.name='Andrew'").list();
        System.out.println("Count on start = " + people0.size());

        try {Thread.sleep(4000);} catch (InterruptedException e) {e.printStackTrace();}


        List<TransactionEntity> people = session.createQuery("from TransactionEntity p where p.name='Andrew'").list();
        System.out.println("Count on the end = " + people.size());

        TransactionEntity john = new TransactionEntity();
        john.setName("John");
        session.save(john);
        transaction.commit();
        session.close();
        System.out.println("Second transaction commit");
    }

}
