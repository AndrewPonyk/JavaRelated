package de.thtesche.udemy;

import de.thtesche.udemy.dao.TeamDao;
import de.thtesche.udemy.domain.Player;
import de.thtesche.udemy.domain.Team;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.PostConstruct;
import javax.servlet.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.ShallowEtagHeaderFilter;


@SpringBootApplication
public class MicroservicesBootApplication extends SpringBootServletInitializer {

  @Autowired
  TeamDao teamDao;

  @Bean
  public Filter shallowEtagHeaderFilter() {
    return new ShallowEtagHeaderFilter();
  }

  public static void main(String[] args) {
    SpringApplication.run(MicroservicesBootApplication.class, args);
  }

  @PostConstruct
  public void init() {
    Set<Player> players = new HashSet<>();
    players.add(new Player("Player 1", "Position 1"));
    players.add(new Player("Player 3", "Position 3"));

    Team team = new Team("A-Team", "Berlin", players);
    team.setMascote("Berlin bear");
    teamDao.save(team);
  }

}
