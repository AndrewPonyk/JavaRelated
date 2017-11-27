package com.ap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @Autowired
    private RestService restService;

    @RequestMapping("/")
    public String getSomeInfoFromExternalResource(){
        return restService.getSomeExternalResource();
    }
}
