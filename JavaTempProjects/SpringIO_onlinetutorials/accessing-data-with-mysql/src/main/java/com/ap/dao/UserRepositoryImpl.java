package com.ap.dao;

import com.ap.model.User;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.List;

@Transactional
public class UserRepositoryImpl implements UserRepositoryCustom {

    @PersistenceContext
    EntityManager entityManager;

    @Override
    public List<User> findByRegex(String regex) {
        List resultList = entityManager.createQuery("select u from User u where u.name like :username", User.class)
                .setParameter("username", "%" + regex + "%")
                .getResultList();

        return resultList;
    }
}
