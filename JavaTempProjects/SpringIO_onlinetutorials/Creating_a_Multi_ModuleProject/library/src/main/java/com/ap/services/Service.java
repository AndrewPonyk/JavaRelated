package com.ap.services;

@org.springframework.stereotype.Service
public class Service {
    private final String message;

    public Service(String message) {
        this.message = message;
    }

    public String getMessage(){
        return message;
    }
}
