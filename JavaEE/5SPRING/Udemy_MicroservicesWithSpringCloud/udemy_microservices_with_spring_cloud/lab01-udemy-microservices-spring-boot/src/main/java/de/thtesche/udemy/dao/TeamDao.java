package de.thtesche.udemy.dao;

import de.thtesche.udemy.domain.Team;
import java.util.List;
import org.springframework.data.repository.CrudRepository;

/**
 *
 * @author thtesche
 */
public interface TeamDao extends CrudRepository<Team, Long> {

  Team findByName(String name);


}
