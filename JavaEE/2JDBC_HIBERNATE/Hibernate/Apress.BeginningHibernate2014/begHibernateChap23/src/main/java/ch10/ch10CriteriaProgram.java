package ch10;

import ch3.Person;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.*;
import util.SessionUtil;
import java.util.List;

public class ch10CriteriaProgram {
    public static void main(String[] args) {
        System.out.println("Using Criteria api");

        saveSeveralPersons();
        firstCriteriaExample();
        secondCriteriaExample();
        thirdCriteriaExampleWithSQL();
        fourthCriteriaExampleProjectionsAndAggregation();
        SessionUtil.closeFactory();
    }

    private static void firstCriteriaExample() {
        System.out.println("SIMPLE eq RESTRICTION");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Criteria crit1 = session.createCriteria(Person.class);
        crit1.add(Restrictions.eq("name", "p1"));
        List<Person> list = crit1.list();

        System.out.println(list.size() + " : " + list.get(0).getName());

        transaction.commit();
        session.close();
        System.out.println("===============");
    }

    private static void secondCriteriaExample() {
        System.out.println("TWO RESTRICTIONS like");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Criteria crit1 = session.createCriteria(Person.class);
        crit1.add(Restrictions.or(Restrictions.like("name", "%2"), Restrictions.like("name", "%3")));

        List<Person> list = crit1.list();

        System.out.println(list.size() + " : " + list.get(0).getName());
        System.out.println(list.size() + " : " + list.get(1).getName());

        transaction.commit();
        session.close();
        System.out.println("-------------------");
    }

    private static void thirdCriteriaExampleWithSQL() {
        System.out.println("USING SQL in RESTRICTIONS");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Criteria crit1 = session.createCriteria(Person.class);
        crit1.add(Restrictions.sqlRestriction("{alias}.id between 1 and 2"));
        crit1.addOrder(Order.desc("id"));

        List<Person> list = crit1.list();

        System.out.println(list.size() + " : " + list.get(0).getId() + ":" + list.get(0).getName());
        System.out.println(list.size() + " : " + list.get(1).getId() + ":" + list.get(1).getName());
        transaction.commit();
        session.close();
        System.out.println("-------------------");
    }

    private static void fourthCriteriaExampleProjectionsAndAggregation() {
        System.out.println("USING Projection and Aggregation in Criteria");
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Criteria crit1 = session.createCriteria(Person.class);
        crit1.add(Restrictions.sqlRestriction("{alias}.id = 1 or {alias}.id = 2"));
        ProjectionList projList = Projections.projectionList();
        projList.add(Projections.property("name"));
        crit1.setProjection(projList);

        List<Object> list = crit1.list();
        System.out.println(list.get(0));
        System.out.println(list.get(1));

        transaction.commit();
        session.close();
        System.out.println("-------------------");
    }

    private static void saveSeveralPersons() {
        Session session = SessionUtil.getSession();
        Transaction transaction = session.beginTransaction();

        Person p1 = new Person();p1.setName("p1");
        Person p2 = new Person();p2.setName("p2");
        Person p3 = new Person();p3.setName("p3");

        session.save(p1);
        session.save(p2);
        session.save(p3);

        transaction.commit();
        session.close();
    }
}
