package de.thtesche.udemy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class ConfigClientApplication {

  @Value("${demo.property.test}")
  String property;

  public static void main(String[] args) {
    SpringApplication.run(ConfigClientApplication.class, args);
  }

  @RequestMapping("/hello")
  String hello() {
    if (property != null) {
      return property;
    } else {
      return "No property found!";
    }
  }
}
