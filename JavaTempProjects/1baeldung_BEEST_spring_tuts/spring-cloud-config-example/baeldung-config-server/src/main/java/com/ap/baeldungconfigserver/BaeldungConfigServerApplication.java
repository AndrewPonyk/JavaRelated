package com.ap.baeldungconfigserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class BaeldungConfigServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BaeldungConfigServerApplication.class, args);
	}
}
