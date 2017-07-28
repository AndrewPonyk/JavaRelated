package com.ap.dao;


import com.ap.model.User;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

public interface UserRepositoryCustom {
    public User customName(String regex);
}