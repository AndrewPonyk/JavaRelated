package de.thtesche.udemy.feign;

import de.thtesche.udemy.domain.Player;
import de.thtesche.udemy.domain.Team;
import de.thtesche.udemy.feign.FeignClientApplication.TeamClient;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author thtesche
 */
@RestController

public class FeignClientController {

  @Autowired
  LoadBalancerClient loadBalancer;

  @Autowired
  TeamClient teamClient;

  @RequestMapping("/list_teams")
  @ResponseBody
  Collection<Team> listTeams() {
    return teamClient.getTeams().getContent();
  }

  @RequestMapping("/action/add_team")
  @ResponseBody
  String addRandomTeam() {

    Set<Player> players = new HashSet<>();
    players.add(new Player("some guy", "mid field"));
    Team team = new Team("B-Team", "Bonn", players);
    
    teamClient.addTeam(team);

    return "OK";
  }

}
