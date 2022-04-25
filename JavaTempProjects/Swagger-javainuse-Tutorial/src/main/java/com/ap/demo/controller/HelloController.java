package com.ap.demo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
public class HelloController {
    @RequestMapping(method = RequestMethod.GET, value = "/api/javainuse")
    public String hello(){
        return "hello";
    }

    @RequestMapping(method = RequestMethod.POST, value = "/api/create")
    public ResponseEntity<Customer> createEntity(@Valid @RequestBody Customer customer){
        return new ResponseEntity<>(customer, HttpStatus.CREATED);
    }
}
