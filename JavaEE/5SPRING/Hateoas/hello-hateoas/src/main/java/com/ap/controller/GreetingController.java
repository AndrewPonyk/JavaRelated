package com.ap.controller;

import com.ap.dto.Greeting;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mvc.ControllerLinkBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.*;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

@RestController
public class GreetingController {

    @RequestMapping("/greeting")
    public HttpEntity<Greeting> greeting(@RequestParam(value = "name", required = false) String name){

        Greeting greeting = new Greeting();
        greeting.setContent("Hello, " + name);
        greeting.add(linkTo(methodOn(GreetingController.class).greeting(name)).withSelfRel().expand(name));
        return new ResponseEntity<Greeting>(greeting, HttpStatus.OK);
    }



}