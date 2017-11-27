package com.ap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

@Service
public class RestService {

    Logger logger = LoggerFactory.getLogger(RestService.class);

    RestTemplate restTemplate = new RestTemplate();

    public RestService(){
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(5000);
        httpRequestFactory.setConnectTimeout(5000);
        httpRequestFactory.setReadTimeout(5000);
        restTemplate = new RestTemplate(httpRequestFactory);
    }

    @Retryable(value = SocketTimeoutException.class, maxAttemptsExpression = "#{${spring.retry.max-attempts}}",
            backoff = @Backoff(maxDelay = 1000L))
    public String getSomeExternalResource(){
        logger.info("Heeeeeeellllllo");
        return restTemplate.getForObject("http://google111.com", String.class);
    }
}
