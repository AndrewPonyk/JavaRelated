package com.ap.dao;

import com.ap.model.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<User, Integer>, UserRepositoryCustom {
    User findByName(@Param("name") String name);
}
