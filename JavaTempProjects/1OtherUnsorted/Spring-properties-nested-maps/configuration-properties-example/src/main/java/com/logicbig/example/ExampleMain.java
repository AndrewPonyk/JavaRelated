package com.logicbig.example;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

import java.util.List;

@SpringBootApplication
@Configuration
public class ExampleMain {


    @Bean
    public ConversionService conversionService() {
        return new DefaultConversionService();
    }


    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(ExampleMain.class, args);
        MyAppProperties bean = context.getBean(MyAppProperties.class);
        System.out.println(bean);
        System.out.println(bean.getMyList());
    }
}