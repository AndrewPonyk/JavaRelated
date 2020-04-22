package com.demo.dao;

import com.demo.model.Team;
import org.springframework.data.repository.CrudRepository;

public interface TeamDao extends CrudRepository<Team, Long> {
    Team findByName(String name);
}
