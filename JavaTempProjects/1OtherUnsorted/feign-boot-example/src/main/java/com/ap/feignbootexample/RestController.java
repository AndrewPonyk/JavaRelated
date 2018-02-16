package com.ap.feignbootexample;

import com.ap.feignbootexample.feignclients.GitHub;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.stream.Collectors;

@org.springframework.web.bind.annotation.RestController
public class RestController {

    @Autowired
    GitHub gitHub;

    @RequestMapping("/flex")
    public ResponseEntity index(HttpServletRequest request){
        String content = request.getParameterMap().entrySet()
                .stream()
                .map(e ->{
                  return e.getKey() + "=\"" + e.getValue()[0] + "\"";
                } )
                .collect(Collectors.joining(", "));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        System.out.println(content);
        return new ResponseEntity<>("{\n" +
                "  \"season\": \"20182\",\n" +
                "  \"seasonName\": \"adidas Fall/Winter 2018\",\n" +
                "  \"refTable\": \"Efficiency Factor (SMV) Table\",\n" +
                "  \"refData\": [\n" +
                "    {\n" +
                "      \"effFactorSMV\": \"64.00\",\n" +
                "      \"productTypeSMV\": \"21\",\n" +
                "      \"factorySMV\": \"A9G001\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"status\": \"200\",\n" +
                "  \"message\": \"success\"\n" +
                "}", headers, HttpStatus.OK);
    }

    @RequestMapping("/{user}")
    public String index(@PathVariable String user){
        return gitHub.userRepos(user).toString();
    }
}
