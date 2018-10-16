/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ap.hellospringboot;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @RequestMapping(method = RequestMethod.GET, path = "/")
    public String testGet(){
        return "GET";
    }

    @RequestMapping(method = RequestMethod.POST, path = "/")
    public String testPost(@RequestBody String body){
        System.out.println(body);
        return body;
    }
}
