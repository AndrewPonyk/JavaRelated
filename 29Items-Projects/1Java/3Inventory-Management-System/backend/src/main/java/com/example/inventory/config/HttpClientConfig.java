package com.example.inventory.config;

import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    @Bean
    RestClient.Builder restClientBuilder(@Value("${app.forecast.timeout:3s}") Duration timeout) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
