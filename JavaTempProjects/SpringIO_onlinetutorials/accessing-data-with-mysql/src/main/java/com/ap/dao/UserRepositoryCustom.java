package com.ap.dao;


import com.ap.model.User;
import org.springframework.data.repository.query.Param;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

public interface UserRepositoryCustom {
    public List<User> findByRegex(String regex);
}