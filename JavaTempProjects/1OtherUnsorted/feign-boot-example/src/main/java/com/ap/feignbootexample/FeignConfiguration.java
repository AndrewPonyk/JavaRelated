package com.ap.feignbootexample;

import org.springframework.cloud.netflix.feign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.ap.feignbootexample.feignclients")
public class FeignConfiguration {
}
