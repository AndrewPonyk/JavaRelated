package com.ap.helloboot3.controller;

import com.ap.helloboot3.model.Person;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/controller1")
public class Controller1 {

    @GetMapping("/{val1}/{val2}")
    public String countSum(@PathVariable("val1") Integer val1, @PathVariable("val2") Integer val2){
        return val1+val2+"";
    }

    @GetMapping("/person")
    public Person getPerson(){
        Person john = new Person("John", 39);
        john.name();
        return john;
    }

}
