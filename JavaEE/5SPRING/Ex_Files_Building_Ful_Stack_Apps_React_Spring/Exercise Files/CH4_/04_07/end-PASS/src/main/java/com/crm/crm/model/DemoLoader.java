package com.crm.crm.model;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component // |su:18 Registers class as Spring-managed bean, auto-detected via component scanning
public class DemoLoader implements CommandLineRunner { // |su:19 CommandLineRunner.run() executes after app context loads - perfect for data seeding—c

    private final ContactRepository repository;

    @Autowired // |su:20 Explicitly marks constructor for dependency injection (optional since Spring 4.3 for single constructor)
    public DemoLoader(ContactRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... strings) throws Exception {
        this.repository.save(new Contact("Emmanuel", "Henri", "me@me.com")); // |su:21 Seeds initial demo data on startup - H2 in-memory DB resets on each restart
    }
}
