package com.mprog.runner;

import com.mprog.converter.BirthdayConverter;
import com.mprog.entity.Birthday;
import com.mprog.entity.PersonalInfo;
import com.mprog.entity.Role;
import com.mprog.entity.User;
import com.mprog.util.HibernateUtil;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.cfg.Configuration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.BlockingQueue;

public class HibernateRunner {

    public static void main(String[] args) throws SQLException {
//        BlockingQueue<Connection> pool = null;
//        Connection connection = pool.take();
//        Connection connection = DriverManager.getConnection(
//                "jdbc:postgresql://localhost:5432/hib_learn",
//                "db.username",
//                "postgres"
//        );

        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
//            var user = User.builder()
//                    .username("ivan42@gmail.com")
//                    .personalInfo(PersonalInfo.builder()
//                            .firstName("Ivan")
//                            .lastname("Ivanov")
//                            .birthday(new Birthday(LocalDate.of(2001, 11, 6)))
//                            .build())
////                    .info("""
////                            {
////                                "name": "Ivan",
////                                "id": 25
////                            }
////                            """)
//                    .role(Role.ADMIN)
//                    .build();

            session.beginTransaction();
//            session.update(user);
//            session.saveOrUpdate(user);
//            session.delete(user);
//            var user1 = session.get(User.class, "ivan1@gmail.com");
//            var user2 = session.get(User.class, "ivan1@gmail.com");
//
//            session.evict(user1);
//            session.flush();

            //            session.evict(user1);
//            session.clear();
//            session.close();

            session.getTransaction().commit();

            System.out.println("ok");

        }


    }
}

