package com.logicbig.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ExampleMain {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(ExampleMain.class, args);
        MyAppProperties bean = context.getBean(MyAppProperties.class);
        System.out.println(bean);
    }
}