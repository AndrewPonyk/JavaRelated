package com.ap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String[]> result = restTemplate.getForEntity("http://localhost:8080/product/list", String[].class);
        String[] body = result.getBody();
        System.out.println(body[0]);
    }
}
