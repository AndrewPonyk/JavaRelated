package com.mprog.runner;

import com.mprog.entity.*;
import com.mprog.util.HibernateUtil;

import java.sql.SQLException;
import java.time.LocalDate;

public class HibernateRunner3 {

    public static void main(String[] args) throws SQLException {

        var google = Company.builder()
                .name("Amazon")
                .build();

//        var user = User.builder()
//                .username("ivan13@gmail.com")
//                .personalInfo(PersonalInfo.builder()
//                        .firstName("Ksenia")
//                        .lastname("Ivanov")
//                        .birthday(new Birthday(LocalDate.of(2001, 11, 6)))
//                        .build())
//                .company(google)
//                .info("""
//                            {
//                                "name": "Ivan",
//                                "id": 25
//                            }
//                            """)
//                .role(Role.ADMIN)
//                .build();

        try (var sessionFactory = HibernateUtil.buildSessionFactory();
             var session = sessionFactory.openSession()) {
            session.beginTransaction();

//            session.save(google);
//            session.save(user);

//            var user1 = session.get(User.class, 1L);
////            var company = user1.getCompany();
////            var id = company.getId();
////            var name = company.getName();
//            session.evict(user1);

            session.getTransaction().commit();
        }
    }
}

