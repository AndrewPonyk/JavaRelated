package com.ap.restrepositories;

import com.ap.model.User;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(path = "users", collectionResourceRel = "users")
public interface UserRestRepository extends PagingAndSortingRepository<User,Integer>{
    public List<User> findByName(@Param("name")String name);
}
