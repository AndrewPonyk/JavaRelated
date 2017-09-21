package com.ap;

import org.springframework.web.client.RestTemplate;

/**
 * Created by andrii on 21.09.17.
 */
public class PostData {
    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();
        String[] requestBody = new String[]{"123", "456"};
        restTemplate.postForObject("http://localhost:8080/product/add", requestBody, String[].class);
    }
}
