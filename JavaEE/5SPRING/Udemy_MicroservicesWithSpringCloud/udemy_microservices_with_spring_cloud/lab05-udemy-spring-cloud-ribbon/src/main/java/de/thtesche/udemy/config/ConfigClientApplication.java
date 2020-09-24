package de.thtesche.udemy.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
@SpringBootApplication
@EnableDiscoveryClient
public class ConfigClientApplication {

  @Value("${demo.property.test}")
  String property;

  @Autowired
  LoadBalancerClient loadBalancer;

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

  @RequestMapping("/lb-lab-04")
  @ResponseBody
  String lb_lab04() {
    String result = "";

    URI uri = loadBalancer.choose("lab-04").getUri();
    if (uri != null) {
      result = (new RestTemplate()).getForObject(uri + "/content", String.class);
    }

    return result;
  }
}
