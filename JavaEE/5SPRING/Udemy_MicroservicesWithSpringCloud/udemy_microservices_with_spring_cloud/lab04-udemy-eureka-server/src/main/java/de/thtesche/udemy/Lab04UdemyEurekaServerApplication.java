package de.thtesche.udemy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class Lab04UdemyEurekaServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(Lab04UdemyEurekaServerApplication.class, args);
  }
}
