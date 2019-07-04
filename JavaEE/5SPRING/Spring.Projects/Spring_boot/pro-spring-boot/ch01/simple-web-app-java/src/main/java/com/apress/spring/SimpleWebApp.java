package com.apress.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SimpleWebApp {


    public static void main(String[] args) {
        SpringApplication.run(SimpleWebApp.class, args);
    }
}

