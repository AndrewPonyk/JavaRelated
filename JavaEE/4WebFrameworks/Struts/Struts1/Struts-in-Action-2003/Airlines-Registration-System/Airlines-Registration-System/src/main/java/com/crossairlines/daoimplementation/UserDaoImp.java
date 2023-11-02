package com.crossairlines.daoimplementation;

import java.util.List;

import javax.persistence.NoResultException;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.Query;

import com.crossairlines.dao.UserDao;
import com.crossairlines.pojo.User;

public class UserDaoImp extends UserDao {
    SessionFactory sf = null;
    public UserDaoImp() {
        sf = new Configuration().configure().buildSessionFactory();
    }
    @Override
    public User verifyUser(User userauth) {
        Session session = sf.openSession();
        Query<User> query =  session.createQuery("from User where email=:email And password=:password");
        query.setParameter("email", userauth.getEmail());
        query.setParameter("password", userauth.getPassword());
        try {
            System.out.println(query.list());
            System.out.println(query.getSingleResult());
            User user = query.getSingleResult();
            return user;
        }
        catch(NoResultException e) {
            e.printStackTrace();
            return null;
        }
    }
    public User verifyAdmin(User userAuth) {
        Session session = sf.openSession();
        Query<User> query =  session.createQuery("from User where email=:email And password=:password And isAdmin = true");
        query.setParameter("email", userAuth.getEmail());
        query.setParameter("password", userAuth.getPassword());
        try {
            User uauth = query.getSingleResult();
            return uauth;
        }
        catch(NoResultException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean addUser(User userauth) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        try {
            session.save(userauth);
            tx.commit();
            session.close();
            return true;
        }
        catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public User getUserDetails(int id) {
        Session session = sf.openSession();
        Query<User> query =  session.createQuery("from User where userAuthId=:id");
        query.setParameter("id", id);
        try {
            return query.getSingleResult();
        }
        catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void updateUserDetails(User user) {
        Session session = sf.openSession();
        System.out.println("User is dao : ");
        Transaction tx = session.beginTransaction();
        session.update(user);
        tx.commit();
        session.close();
    }
    @Override
    public List<User> getAllUserDetails() {
        Session session = sf.openSession();
        Query<User> query =  session.createQuery("from User");
        return query.getResultList();
    }
    @Override
    public void deleteUser(User user) {
        Session session = sf.openSession();
        Transaction tx = session.beginTransaction();
        session.delete(user);
        tx.commit();
        session.close();
    }


}