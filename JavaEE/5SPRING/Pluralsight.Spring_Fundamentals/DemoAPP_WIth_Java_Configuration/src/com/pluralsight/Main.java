package com.pluralsight;

import com.pluralsight.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

    @Autowired
    static CustomerService customerService;

    public static void main(String[] args) {
        ApplicationContext appContext = new AnnotationConfigApplicationContext(AppConfig.class);
        CustomerService customerService = (CustomerService) appContext.getBean("customerService");
        customerService.findAll().forEach(System.out::println);
    }

}



