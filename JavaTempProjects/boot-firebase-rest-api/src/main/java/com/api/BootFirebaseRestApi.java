package com.api;

import generator.GenerateRandomFirestoreDataApp_test100k;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BootFirebaseRestApi implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(BootFirebaseRestApi.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        GenerateRandomFirestoreDataApp_test100k.populate();
    }
}
