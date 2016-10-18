package com.pluralsight;

import com.pluralsight.service.CustomerService;
import com.pluralsight.service.ServiceA;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MainWith_Autowired {


    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
        ServiceA serviceA = applicationContext.getBean("serviceA", ServiceA.class);
        System.out.println(serviceA.getTestString());
    }

}
