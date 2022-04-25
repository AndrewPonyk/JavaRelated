package com.ap;

import com.ap.service.Greeter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BaeldungUseCustomStarterApplication implements CommandLineRunner {

    @Autowired
    private Greeter greeter;

    public static void main(String[] args) {
        SpringApplication.run(BaeldungUseCustomStarterApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("Test");
        System.out.println(greeter.greet());
    }
}
