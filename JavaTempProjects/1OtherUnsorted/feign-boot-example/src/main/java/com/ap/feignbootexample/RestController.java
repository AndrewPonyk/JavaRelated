package com.ap.feignbootexample;

import com.ap.feignbootexample.feignclients.GitHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    @Autowired
    GitHub gitHub;

    @RequestMapping("/{user}")
    public String index(@PathVariable String user){
        return gitHub.userRepos(user).toString();
    }
}
