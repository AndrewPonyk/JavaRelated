package com.demo.dao;

import com.demo.model.Team;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RestResource;

@RestResource(path = "teams-dao", rel = "teams-dao")
public interface TeamDao extends CrudRepository<Team, Long> {
    Team findByName(String name);
}
