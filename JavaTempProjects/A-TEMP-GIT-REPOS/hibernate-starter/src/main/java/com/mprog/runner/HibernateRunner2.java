package com.mprog.runner;

import com.mprog.converter.BirthdayConverter;
import com.mprog.entity.Birthday;
import com.mprog.entity.PersonalInfo;
import com.mprog.entity.User;
import com.mprog.util.HibernateUtil;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@Slf4j
public class HibernateRunner2 {

//    private static final Logger log = LoggerFactory.getLogger(HibernateRunner2.class);
    public static void main(String[] args) {

//        var user = User.builder()
//                .username("petya21@gmail.com")
//                .personalInfo(PersonalInfo.builder()
//                        .firstName("Test")
//                        .lastname("Testov")
//                        .birthday(new Birthday(LocalDate.of(2001, 11, 6)))
//                        .build())
//                .build();

//        log.info("User entity is in transient state, object: {}", user);
        try (var sessionFactory = HibernateUtil.buildSessionFactory()){
            var session1 = sessionFactory.openSession();
            try (session1) {
                var transaction = session1.beginTransaction();
                log.trace("Transaction  is begun, {}", transaction);

//                session1.saveOrUpdate(user);
//                log.trace("User is in persistent state: {}, session {}", user, session1);
                session1.getTransaction().commit();
            }

//            log.warn("User is in detached state: {}, session: {}", user, session1);
            try (var session2 = sessionFactory.openSession()) {
                var personalInfo = PersonalInfo.builder()
                        .lastname("Testov")
                        .firstName("Test")
//                        .birthday(new Birthday(LocalDate.of(2001, 11, 6)))
                        .build();
//                user.setLastname("Petrov");
                //                session2.delete(user);
//                session2.refresh(user);
//                var merge = session2.merge(user);

                var user2 = session2.get(User.class, personalInfo);
                session2.getTransaction().commit();

            }
        } catch (Exception exception) {
            log.error("Exception occurred", exception);
            throw exception;
        }
    }
}
