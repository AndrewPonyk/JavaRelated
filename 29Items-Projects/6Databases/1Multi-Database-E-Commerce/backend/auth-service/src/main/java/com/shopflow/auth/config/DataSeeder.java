package com.shopflow.auth.config;

import com.shopflow.auth.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo customer and admin on startup so the platform is usable
 * immediately in local/dev. Idempotent — safe to run on every boot.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserService users;

    public DataSeeder(UserService users) {
        this.users = users;
    }

    @Override
    public void run(String... args) {
        users.seedIfAbsent("demo", "demo@shopflow.local", "password123", "ROLE_CUSTOMER");
        users.seedIfAbsent("admin", "admin@shopflow.local", "admin12345", "ROLE_CUSTOMER,ROLE_ADMIN");
    }
}
