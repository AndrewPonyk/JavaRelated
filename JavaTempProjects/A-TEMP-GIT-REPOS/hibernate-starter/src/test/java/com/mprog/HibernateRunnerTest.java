package com.mprog;

import com.mprog.entity.*;
import com.mprog.util.HibernateTestUtil;
import com.mprog.util.HibernateUtil;
import lombok.Cleanup;
import org.hibernate.Session;
import org.hibernate.jpa.QueryHints;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import javax.persistence.Column;
import javax.persistence.FlushModeType;
import javax.persistence.Table;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Optional.*;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;

class HibernateRunnerTest {


    @Test
    void checkHQL() {
        try (var sessionFactory = HibernateTestUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();


            var name = "Ivan";

//            var query = session.createQuery("select u from User u where u.personalInfo.firstName = 'Ivan'", User.class);
//            var result = query.list();
            var list = session
                    .createNamedQuery("findUserByName"
//                            "select u from User u" +
////                                    " join u.company c" +
//                                    " where u.personalInfo.firstName = :firstName and u.company.name = :companyName" +
//                                    " order by u.personalInfo.lastname desc"
                            , User.class
                    )
                    .setParameter("firstName", name)
                    .setParameter("companyName", "Google")
                    .setFlushMode(FlushModeType.AUTO)
                    .setHint(QueryHints.HINT_FLUSH_MODE, "auto")
                    .list();



            int i = session.createQuery("update User u set u.role = 'ADMIN'").executeUpdate();

            NativeQuery nativeQuery = session.createNativeQuery("select * from users where username = 'tete'");
            System.out.println();

            session.getTransaction().commit();
        }

    }

    @Test
    void checkJoinedTableInheritance() {
        //        + and - of joined table
//        +
//        данные нормализованы(общие поля в одной таблице, а отдельные поле в разных таблицах
//        -
//        ухудшение при вставке, делит, и обновления( используется сразу две таблицы)


        try (var sessionFactory = HibernateTestUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

            Company company = Company.builder()
                    .name("Google")
                    .build();

            session.save(company);

//            Programmer programmer = Programmer.builder()
//                    .username("testivan@gmail.com")
//                    .language(Language.C)
//                    .company(company)
//                    .build();
//
//            session.save(programmer);
//
//            Manager manager = Manager.builder()
//                    .username("test5@mail.ru")
//                    .projectName("rpl")
//                    .company(company)
//                    .build();
//
//            session.save(manager);

            session.flush();

//            var programmer1 = session.get(Programmer.class, 1L);
            var manager1 = session.get(User.class, 2L);
            System.out.println(company);
            session.getTransaction().commit();
        }
    }

    @Test
    void checkSingleTableInheritance(){
        //        + and - of single table
//      -
//      one table with all fields
//      для общих полей нельзя задать конмтрейнты
//        +
//        запрос только на одну таблицу(без юнионов)
//      можно юзать IDENTITY
        try (var sessionFactory = HibernateTestUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

            Company company = Company.builder()
                    .name("Google")
                    .build();

            session.save(company);

//            Programmer programmer = Programmer.builder()
//                    .username("testivan@gmail.com")
//                    .language(Language.C)
//                    .company(company)
//                    .build();
//
//            session.save(programmer);
//
//            Manager manager = Manager.builder()
//                    .username("test5@mail.ru")
//                    .projectName("rpl")
//                    .company(company)
//                    .build();
//
//            session.save(manager);
//
//            session.flush();
//
//            var programmer1 = session.get(Programmer.class, 1L);
            var manager1 = session.get(User.class, 2L);
            System.out.println(company);
            session.getTransaction().commit();
        }
    }

    @Test
    void checkH2AndTablePerClass(){
//        + and - of table per class
//        -
//        1 - дублирование
//        2 - union объединение двух таблиц
//        3 - нужно юзать sequence один общий
//        +
//        1 - если мы знаем что нам нужно, то можем обращаться к нужной нам таблице
        try (var sessionFactory = HibernateTestUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

             Company company = Company.builder()
                    .name("Google")
                    .build();

            session.save(company);

//            Programmer programmer = Programmer.builder()
//                    .username("testivan@gmail.com")
//                    .language(Language.C)
//                    .company(company)
//                    .build();
//
//            session.save(programmer);
//
//            Manager manager = Manager.builder()
//                    .username("test5@mail.ru")
//                    .projectName("rpl")
//                    .company(company)
//                    .build();
//
//            session.save(manager);
//
//            session.flush();
//
//            var programmer1 = session.get(Programmer.class, 1L);
            var manager1 = session.get(User.class, 2L);
            System.out.println(company);
            session.getTransaction().commit();
        }
    }

    @Test
    void localeInfo(){
        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

            Company company = session.get(Company.class, 1);
//            company.getLocales().put("sp", "Spanish на русском");
//            company.getLocales().put("us", "American description");
//
//            company.getLocales().forEach((k, v) -> System.out.println(k + " - " + v));
//            company.getUsers().forEach((k, v) -> System.out.println(v));
            session.getTransaction().commit();
        }
    }
    @Test
    void checkManyToMany(){
        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();


            var user = session.get(User.class, 7L);
            Chat chat = session.get(Chat.class, 1L);


            UserChat userChat = UserChat.builder()
//                    .createdAt(Instant.now())
//                    .created_by(user.getUsername())
                    .build();

            userChat.setUser(user);
            userChat.setChat(chat);

            session.save(userChat);
            //            user.getChats().clear();
//            Chat mdev = Chat.builder()
//                    .name("mdev")
//                    .build();
//
//            user.addChat(mdev);
//            session.save(mdev);

            session.getTransaction().commit();
        }
    }
    @Test
    void checkOneToOne() {
        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();


//            var user = User.builder()
//                    .username("testUser1@mail.ru")
//                    .build();

            var profile = Profile.builder()
                    .language("ru")
                    .street("udarnikov, 29")
                    .build();

//            profile.setUser(user);
//
//            session.save(user);
//            profile.setUser(user);
//            session.save(profile);

            session.getTransaction().commit();
        }
    }

    @Test
    void checkOrphanRemoval() {
//        Company company = null;
        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

            var company = session.get(Company.class, 1);
//            company.getUsers().removeIf(user -> user.getId() == 3L);
            session.getTransaction().commit();
        }
    }

    @Test
    void checkLazyInitialization() {
        Company company = null;
        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

            company = session.get(Company.class, 1);

            session.getTransaction().commit();
        }

        var users = company.getUsers();
        System.out.println(users.size());
    }

    @Test
    void deleteCompany() {
        @Cleanup var sessionFactory = HibernateUtil.buildSessionFactory();
        @Cleanup Session session = sessionFactory.openSession();

        session.beginTransaction();

        var company = session.get(Company.class, 3);
        session.delete(company);

        session.getTransaction().commit();
    }


    @Test
    void addUserToNewCompany() {
        @Cleanup var sessionFactory = HibernateUtil.buildSessionFactory();
        @Cleanup Session session = sessionFactory.openSession();

        session.beginTransaction();

        var company = Company.builder()
                .name("Facebook")
                .build();

//        var sveta = User.builder().username("sveta@gmail.com").build();
//        company.addUser(sveta);

        session.save(company);

        session.getTransaction().commit();
    }

    @Test
    void oneToMany() {
        @Cleanup var sessionFactory = HibernateUtil.buildSessionFactory();
        @Cleanup Session session = sessionFactory.openSession();

        session.beginTransaction();
        var company = session.get(Company.class, 1);
        System.out.println("");
        session.getTransaction().commit();

    }

    @Test
    void checkGetReflectionApi() throws SQLException, NoSuchMethodException, NoSuchFieldException, InvocationTargetException, InstantiationException, IllegalAccessException {
        PreparedStatement preparedStatement = null;
        var resultSet = preparedStatement.executeQuery();
        resultSet.getString("username");
        resultSet.getString("firstName");
        resultSet.getString("lastName");

        var clazz = User.class;
        var constructor = clazz.getConstructor();
        var user = constructor.newInstance();
        var usernameField = clazz.getDeclaredField("username");
        usernameField.setAccessible(true);
        usernameField.set(user, resultSet.getString("username"));
    }

    @Test
    void checkReflectionApi() throws SQLException, IllegalAccessException {
//        var user = User.builder()
//                .username("ivan@gmail.com")
////                .firstName("Ivan")
////                .lastname("Ivanov")
////                .birthday(new Birthday(LocalDate.of(2001, 11, 6)))
//                .build();

        String sql = """
                insert
                into
                %s
                (%s)
                values
                (%s)
                """;
//        var tableName = ofNullable(user.getClass().getAnnotation(Table.class))
//                .map(tableAnnotation -> tableAnnotation.schema() + "." + tableAnnotation.name())
//                .orElse(user.getClass().getName());
//        if (tableName.startsWith(".")) {
//            tableName = tableName.substring(1);
//        }
//
//        var declaredFields = user.getClass().getDeclaredFields();
//
//        var columnNames = Arrays.stream(declaredFields)
//                .map(field -> ofNullable(field.getAnnotation(Column.class))
//                        .map(Column::name)
//                        .orElse(field.getName()))
//                .collect(joining(", "));
//
//        var columnValues = Arrays.stream(declaredFields)
//                .map(field -> "?")
//                .collect(joining(", "));
//
//        var sqlString = sql.formatted(tableName, columnNames, columnValues);
//        System.out.println(sqlString);
//
//        Connection connection = null;
//        var preparedStatement = connection.prepareStatement(sqlString);
//
//        for (Field declaredField : declaredFields) {
//            declaredField.setAccessible(true);
//            preparedStatement.setObject(1, declaredField.get(user));
//        }

    }
}