package com.ap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class SpringRetryExampleApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringRetryExampleApplication.class, args);
	}
}
