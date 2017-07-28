package com.ap.controller;

import com.ap.dao.UserRepository;
import com.ap.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/demo")
public class MainConroller {

    @Autowired
    UserRepository userRepository;

    @RequestMapping("/add")
    public
    @ResponseBody
    String addUser(@RequestParam(required = false) String name,
                   @RequestParam(required = false) String email) {
        User newUser = new User();
        newUser.setName(name);
        newUser.setEmail(email);
        userRepository.save(newUser);

        return "added";
    }

    @RequestMapping("/all")
    public
    @ResponseBody
    Iterable<User> getAllUsers() {
        return userRepository.findAll();
    }

    @RequestMapping("/findByName")
    public @ResponseBody User findByName(@RequestParam String name){
        User user = userRepository.findByName(name);
        return user;
    }

    @RequestMapping("/findByRegex")
    public @ResponseBody User findByRegex(@RequestParam String name){
        User user = userRepository.customName(name);
        return user;
    }
}
