package com.crm.crm.model;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // |su:1 Combines @Configuration + @EnableAutoConfiguration + @ComponentScan - main Spring Boot entry point—c
public class ReactAndSpringDataRestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReactAndSpringDataRestApplication.class, args); // |su:2 Bootstraps application: creates ApplicationContext, starts embedded server, auto-configures beans
    }
}
