package com.mprog.runner;


import com.mprog.dao.CompanyRepository;
import com.mprog.dao.PaymentRepository;
import com.mprog.dao.UserRepository;
import com.mprog.dto.UserCreateDto;
import com.mprog.entity.*;
import com.mprog.interceptor.TransactionInterceptor;
import com.mprog.mapper.CompanyReadMapper;
import com.mprog.mapper.UserCreateMapper;
import com.mprog.mapper.UserReadMapper;
import com.mprog.service.UserService;
import com.mprog.util.HibernateUtil;
import com.mprog.util.TestDataImporter;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.graph.RootGraph;
import org.hibernate.graph.SubGraph;
import org.hibernate.jpa.QueryHints;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class HibernateRunner4 {

    @Transactional
    public static void main(String[] args) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
//        pessimisticAndOptimisticLocksLesson();
//        transactionsPart();
//        envers();
//        cacheLessons();
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory()) {

            Session session = (Session) Proxy.newProxyInstance(SessionFactory.class.getClassLoader(), new Class[]{Session.class},
                    (proxy, method, args1) -> method.invoke(sessionFactory.getCurrentSession(), args1));


            CompanyReadMapper companyReadMapper = new CompanyReadMapper();
            CompanyRepository companyRepository = new CompanyRepository(session);
            UserReadMapper userReadMapper = new UserReadMapper(companyReadMapper);
            UserCreateMapper userCreateMapper = new UserCreateMapper(companyRepository);
            UserRepository userRepository = new UserRepository(session);
//            UserService userService = new UserService(userRepository, userReadMapper, userCreateMapper);
            TransactionInterceptor transactionInterceptor = new TransactionInterceptor(sessionFactory);

            UserService userService = new ByteBuddy()
                    .subclass(UserService.class)
                    .method(ElementMatchers.any())
                    .intercept(MethodDelegation.to(transactionInterceptor))
                    .make()
                    .load(UserService.class.getClassLoader())
                    .getLoaded()
                    .getDeclaredConstructor(UserRepository.class, UserReadMapper.class, UserCreateMapper.class)
                    .newInstance(userRepository, userReadMapper, userCreateMapper);

            userService.findById(1L).ifPresent(System.out::println);

            UserCreateDto userCreateDto = new UserCreateDto(
                    PersonalInfo.builder()
                            .firstName("Liza")
                            .lastname("Stepanova")
                            .birthday(LocalDate.now())
                            .build(),
                    "liza2@gmail.com",
                    null,
                    Role.USER,
                    1
            );
            userService.create(userCreateDto);



        }
    }

    private static void cacheLessons() {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory()) {

            User user = null;
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                user = session.find(User.class, 1L);
                user.getCompany().getName();
                user.getUserChats().size();
                var user1 = session.find(User.class, 1L);

                List<Payment> payments = session.createQuery("select m from Payment m where m.receiver.id = :userId", Payment.class)
                        .setParameter("userId", user.getId())
                        .setCacheable(true)
//                        .setCacheRegion("queries")
//                        .setHint(QueryHints.HINT_CACHEABLE, true)
                        .getResultList();
                session.getTransaction().commit();
            }

            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();

                var user2 = session.find(User.class, 1L);
                user2.getCompany().getName();
                user2.getUserChats().size();

                List<Payment> payments = session.createQuery("select m from Payment m where m.receiver.id = :userId", Payment.class)
                        .setParameter("userId", user2.getId())
                        .setCacheable(true)
//                        .setCacheRegion("queries")
//                        .setHint(QueryHints.HINT_CACHEABLE, true)
                        .getResultList();
                session.getTransaction().commit();
            }
        }
    }

    private static void envers() {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory()) {
//            TestDataImporter.importData(sessionFactory);

            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                var payment = session.find(Payment.class, 1L);
                payment.setAmount(payment.getAmount() + 10);
                session.getTransaction().commit();
            }

            try (Session session2 = sessionFactory.openSession()) {
                session2.beginTransaction();

                AuditReader auditReader = AuditReaderFactory.get(session2);
//                auditReader.find(Payment.class, 1L, 1L)
                Payment oldPayment = auditReader.find(Payment.class, 1L, new Date(1648935229572L));
                System.out.println();

                session2.getTransaction().commit();
            }

            //            List<User> users = session.createQuery("select u from User u", User.class).list();
//
//            Chat chat = Chat.builder()
//                    .name("dmdev")
//                    .build();
//
//            List<UserChat> userChats = new ArrayList<>();
//
//            for (User user : users) {
//                UserChat userChat = UserChat.builder()
//                        .chat(chat)
//                        .user(user)
//                        .build();
//                userChats.add(userChat);
//                session.save(userChat);
//            }
//
//            chat.setUserChats(userChats);
//            session.save(chat);
        }
    }

    private static void transactionsPart() {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory();
             Session session = sessionFactory.openSession()) {
            TestDataImporter.importData(sessionFactory);

//            session.doWork(connection -> connection.setAutoCommit(false));
            //            session.setDefaultReadOnly(true);
//            session.setReadOnly();
//            session.beginTransaction();

            Profile profile = Profile.builder()
                    .user(session.find(User.class, 1L))
                    .language("ru")
                    .street("Kolas 28")
                    .build();

            session.save(profile);


//            session.createNativeQuery("SET TRANSACTION READ ONLY;").executeUpdate();
            List<Payment> payments = session.createQuery("select u from Payment u", Payment.class)
//                    .setLockMode(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
//                    .setHint("javax.persistence.lock.timeout", 5000)
//                    .setReadOnly(true)
//                    .setHint(QueryHints.HINT_READONLY, true)
                    .list();

            Payment payment = session.find(Payment.class, 1L);
            payment.setAmount(payment.getAmount() + 10);
//            session.save(payment);
//            session.flush();

//            session.getTransaction().commit();
        }
    }

    private static void pessimisticAndOptimisticLocksLesson() {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory();
             Session session = sessionFactory.openSession();
             Session session1 = sessionFactory.openSession()) {
            TestDataImporter.importData(sessionFactory);
            session.beginTransaction();
            session1.beginTransaction();

            session.createQuery("select u from Payment u", Payment.class)
                    .setLockMode(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
                    .setHint("javax.persistence.lock.timeout", 5000)
                    .list();

            Payment payment = session.find(Payment.class, 1L, LockModeType.PESSIMISTIC_READ);
            payment.setAmount(payment.getAmount() + 10);

            Payment theSamePayment = session1.find(Payment.class, 1L);
            theSamePayment.setAmount(payment.getAmount() + 20);

            session1.getTransaction().commit();
            session.getTransaction().commit();
        }
    }

    private static void beforeTransactionLessonsCode() {
        try (SessionFactory sessionFactory = HibernateUtil.buildSessionFactory();
             Session session = sessionFactory.openSession()) {

            session.beginTransaction();

            RootGraph<User> entityGraph = session.createEntityGraph(User.class);
            entityGraph.addAttributeNodes("company", "userChats");
            SubGraph<UserChat> userChats = entityGraph.addSubGraph("userChats", UserChat.class);
            userChats.addAttributeNode("chat");
//            session.enableFetchProfile("withCompanyAndPayments");
            RootGraph<?> graph = session.getEntityGraph("withCompanyAndChat");


            Map<String, Object> properties = Map.of(
                    GraphSemantic.LOAD.getJpaHintName(), graph
            );
            User user = session.find(User.class, 1L, properties);

            System.out.println(user.getCompany().getName());
            System.out.println(user.getUserChats().size());


            List<User> users = session.createQuery("select u from User u " +
                            "join fetch u.payments where 1 = 1", User.class)
//                    .setHint(GraphSemantic.LOAD.getJpaHintName(), properties)
//                    .setHint(GraphSemantic.LOAD.getJpaHintName(), session.getEntityGraph("withCompanyAndChat"))
                    .setHint(GraphSemantic.LOAD.getJpaHintName(), entityGraph)
                    .list();
            users.forEach(it -> System.out.println(it.getUserChats().size()));
//            users.forEach(user -> System.out.println(user.getUserChats()));
            users.forEach(it -> System.out.println(it.getCompany().getName()));

            session.getTransaction().commit();
        }
    }

}

