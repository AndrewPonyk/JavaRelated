package de.thtesche.udemy.feign;

import de.thtesche.udemy.domain.Team;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.hateoas.Resources;
import org.springframework.hateoas.config.EnableHypermediaSupport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@SpringBootApplication
@EnableDiscoveryClient
@EnableHypermediaSupport(type = EnableHypermediaSupport.HypermediaType.HAL)
@EnableFeignClients
public class FeignClientApplication {

  public static void main(String[] args) {
    SpringApplication.run(FeignClientApplication.class, args);
  }

  @FeignClient("lab-04")
  public interface TeamClient {

    @RequestMapping(method = RequestMethod.GET, value = "/teams?projection=inlinePlayers")
    Resources<Team> getTeams();

    @RequestMapping(method = RequestMethod.POST, value = "/teams", consumes = MediaType.APPLICATION_JSON_VALUE)
    void addTeam(@RequestBody Team team);

  }

}
