package com.ap.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

public class Greeter {

    private GreetingConfig greetingConfig;

    public Greeter(GreetingConfig greetingConfig) {
        this.greetingConfig = greetingConfig;
    }

    public String greet() {
        return "Hello " + greetingConfig.getProperty("baeldung.greeter.username")
                + LocalDateTime.now();
    }
}
