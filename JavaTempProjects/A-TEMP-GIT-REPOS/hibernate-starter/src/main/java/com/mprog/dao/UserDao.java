package com.mprog.dao;

import com.mprog.dto.CompanyDto;
import com.mprog.dto.PaymentFilter;
import com.mprog.entity.*;
import com.mprog.functions.MyFunction;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Visitor;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.Session;

import javax.persistence.criteria.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.mprog.entity.QCompany.company;
import static com.mprog.entity.QPayment.payment;
import static com.mprog.entity.QUser.user;

//import static com.mprog.entity.Company_.users;
//import static com.mprog.entity.PersonalInfo_.birthday;
//import static com.mprog.entity.PersonalInfo_.firstName;
//import static com.mprog.entity.User_.personalInfo;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserDao {

    private static final UserDao INSTANCE = new UserDao();

    /**
     * Возвращает всех сотрудников
     */
    public List<User> findAll(Session session) {
//        HQL
//        return session.createQuery("select u from User u", User.class)
//                .list();

//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<User> criteria = cb.createQuery(User.class);
//        Root<User> user = criteria.from(User.class);
//        criteria.select(user);
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<User>(session)
                .select(user)
                .from(user)
                .fetch();
    }

    /**
     * Возвращает всех сотрудников с указанным именем
     */
    public List<User> findAllByFirstName(Session session, String firstNameArg) {
//        HQL
//        return session
//                .createQuery("select u from User u where u.personalInfo.firstName = :firstName", User.class)
//                .setParameter("firstName", firstName)
//                .list();


//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<User> criteria = cb.createQuery(User.class);
//        Root<User> user = criteria.from(User.class);
//        criteria.select(user)
//                .where(
//                        cb.equal(user.get(personalInfo).get(firstName), firstNameArg)
//                );
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<User>(session)
                .select(user)
                .from(user)
                .where(user.personalInfo.firstName.eq(firstNameArg))
                .fetch();
    }

    /**
     * Возвращает первые {limit} сотрудников, упорядоченных по дате рождения (в порядке возрастания)
     */
    public List<User> findLimitedUsersOrderedByBirthday(Session session, int limit) {
//        HQL
//        return session
//                .createQuery("select u from User u order by u.personalInfo.birthday", User.class)
//                .setMaxResults(limit)
//                .list();


//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<User> criteria = cb.createQuery(User.class);
//        Root<User> user = criteria.from(User.class);
//        criteria.select(user).orderBy(cb.asc(user.get(personalInfo).get(birthday)));
//        return session.createQuery(criteria).setMaxResults(limit).list();
        return new JPAQuery<User>(session)
                .select(user)
                .from(user)
                .orderBy(user.personalInfo.birthday.asc())
                .limit(limit)
                .fetch();
    }

    /**
     * Возвращает всех сотрудников компании с указанным названием
     */
    public List<User> findAllByCompanyName(Session session, String companyName) {
//        HQL
//        return session
//                .createQuery("select u from Company c " +
//                        "inner join c.users u " +
//                        "where c.name = :companyName", User.class)
//                .setParameter("companyName", companyName)
//                .list();


//        Criteria API
//        var cb = session.getCriteriaBuilder();
//        var criteria = cb.createQuery(User.class);
//        var company = criteria.from(Company.class);
//        var companyUsers = company.join(users);
//        criteria.select(companyUsers).where(cb.equal(company.get(Company_.name), companyName));
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<User>(session)
                .select(user)
                .from(company)
                .join(company.users, user)
                .where(company.name.eq(companyName))
                .fetch();

    }

    /**
     * Возвращает все выплаты, полученные сотрудниками компании с указанными именем,
     * упорядоченные по имени сотрудника, а затем по размеру выплаты
     */
    public List<Payment> findAllPaymentsByCompanyName(Session session, String companyName) {
//        HQL
//        return session
//                .createQuery("select p from Payment p " +
//                        "join p.receiver u " +
//                        "join u.company c " +
//                        "where c.name = :companyName " +
//                        "order by u.personalInfo.firstName, p.amount", Payment.class)
//                .setParameter("companyName", companyName)
//                .list();


//        Criteria API
////        getting cb
//        CriteriaBuilder cb = session.getCriteriaBuilder();
////        creating criteria. should put what we need in return
//        CriteriaQuery<Payment> criteria = cb.createQuery(Payment.class);
////        choosing from
//        Root<Payment> payment = criteria.from(Payment.class);
////        joining
//        Join<Payment, User> receiver = payment.join(Payment_.receiver);
//        payment.fetch(Payment_.receiver);
////        joining
//        Join<User, Company> company = receiver.join(User_.company);
////        query
//        criteria.select(payment)
//                .where(cb.equal(company.get(Company_.name), companyName))
//                .orderBy(
//                        cb.asc(receiver.get(User_.personalInfo).get(PersonalInfo_.firstName)),
//                        cb.asc(payment.get(Payment_.amount))
//                );
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<Payment>(session)
                .select(payment)
                .from(payment)
                .join(payment.receiver, user).fetchJoin()
                .join(user.company, company)
                .where(company.name.eq(companyName))
                .orderBy(user.personalInfo.firstName.asc(), payment.amount.asc())
                .fetch();
    }

    /**
     * Возвращает среднюю зарплату сотрудника с указанными именем и фамилией
     */
    public Double findAveragePaymentAmountByFirstAndLastNames(Session session, PaymentFilter filter) {
//        HQL
//        return session
//                .createQuery("select avg(p.amount) from Payment p " +
//                        "join p.receiver u " +
//                        "where u.personalInfo.firstName = :fName " +
//                        "and u.personalInfo.lastname = :lName", Double.class)
//                .setParameter("fName", firstName)
//                .setParameter("lName", lastName)
//                .uniqueResult();


//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<Double> criteria = cb.createQuery(Double.class);
//        Root<Payment> payment = criteria.from(Payment.class);
//        Join<Payment, User> receiver = payment.join(Payment_.receiver);
//        List<Predicate> predicates = new ArrayList<>();
//        if (firstName != null)
//            predicates.add(cb.equal(receiver.get(personalInfo).get(PersonalInfo_.firstName), firstName));
//        if (lastName != null)
//            predicates.add(cb.equal(receiver.get(personalInfo).get(PersonalInfo_.lastname), lastName));
//        criteria.select(cb.avg(payment.get(Payment_.amount)))
//                .where(
//                        predicates.toArray(Predicate[]::new)
//                );
//        return session.createQuery(criteria)
//                .uniqueResult();
        Predicate predicate = QPredicate.builder()
//                .add(filter.getFirstName(), (value) -> user.personalInfo.firstName.eq(value))
                .add(filter.getFirstName(), user.personalInfo.firstName::eq)
                .addP(filter.getLastName(), new MyFunction<String, Predicate>() {
                    @Override
                    public Predicate apply(String s) {
                        return user.personalInfo.lastname.eq(s);
                    }
                }).buildAnd();

        return new JPAQuery<Double>(session)
                .select(payment.amount.avg())
                .from(payment)
                .join(payment.receiver, user)
                .where(predicate)
                .fetchOne();
    }

    /**
     * Возвращает для каждой компании: название, среднюю зарплату всех её сотрудников. Компании упорядочены по названию.
     */
    public List<Tuple> findCompanyNamesWithAvgUserPaymentsOrderedByCompanyName(Session session) {
//        HQL
//        return session
//                .createQuery("select c.name, avg(p.amount) from Company c " +
//                        "join c.users u " +
//                        "join u.payments p " +
//                        "group by c.name " +
//                        "order by c.name", Object[].class)
//                .list();


//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<CompanyDto> criteria = cb.createQuery(CompanyDto.class);
//        Root<Company> company = criteria.from(Company.class);
//        MapJoin<Company, String, User> users = company.join(Company_.users);
//        ListJoin<User, Payment> payment = users.join(User_.payments);
////        criteria.multiselect(company.get(Company_.name), cb.avg(payment.get(Payment_.amount)))
////                .groupBy(company.get(Company_.name))
////                .orderBy(cb.asc(company.get(Company_.name)));
//        criteria.select(
//                        cb.construct(
//                                CompanyDto.class,
//                                company.get(Company_.name), cb.avg(payment.get(Payment_.amount))
//                        )
//                )
//                .groupBy(company.get(Company_.name))
//                .orderBy(cb.asc(company.get(Company_.name)));
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<Tuple>(session)
                .select(company.name, payment.amount.avg())
                .from(company)
                .join(company.users, user)
                .join(user.payments, payment)
                .groupBy(company.name)
                .orderBy(company.name.asc())
                .fetch();
    }

    /**
     * Возвращает список: сотрудник (объект User), средний размер выплат, но только для тех сотрудников, чей средний размер выплат
     * больше среднего размера выплат всех сотрудников
     * Упорядочить по имени сотрудника
     */
    public List<Tuple> isItPossible(Session session) {
//        HQL
//        return session
//                .createQuery("select u, avg(p.amount) from User u " +
//                        "join u.payments p " +
//                        "group by u " +
//                        "having avg(p.amount) > (select avg(p.amount) from Payment p) " +
//                        "order by u.personalInfo.firstName", Object[].class)
//                .list();


//        Criteria API
//        CriteriaBuilder cb = session.getCriteriaBuilder();
//        CriteriaQuery<Tuple> criteria = cb.createQuery(Tuple.class);
//        Root<User> user = criteria.from(User.class);
//        ListJoin<User, Payment> payments = user.join(User_.payments);
//
//        Subquery<Double> subquery = criteria.subquery(Double.class);
//        Root<Payment> paymentSubQuery = subquery.from(Payment.class);
//
//        criteria.select(
//                        cb.tuple(
//                                user,
//                                cb.avg(payments.get(Payment_.amount))
//                        )
//                ).groupBy(user.get(User_.id))
//                .having(cb.gt(
//                        cb.avg(payments.get(Payment_.amount)),
//                        subquery.select(cb.avg(paymentSubQuery.get(Payment_.amount)))
//                        ))
//                .orderBy(cb.asc(user.get(personalInfo).get(firstName)));
//
//        return session.createQuery(criteria)
//                .list();
        return new JPAQuery<Tuple>(session)
                .select(user, payment.amount.avg())
                .from(user)
                .join(user.payments, payment)
                .groupBy(user.id)
                .having(payment.amount.avg().gt(
                        new JPAQuery<Double>(session)
                                .select(payment.amount.avg())
                                .from(payment)
                ))
                .orderBy(user.personalInfo.firstName.asc())
                .fetch();
    }

    public static UserDao getInstance() {
        return INSTANCE;
    }
}