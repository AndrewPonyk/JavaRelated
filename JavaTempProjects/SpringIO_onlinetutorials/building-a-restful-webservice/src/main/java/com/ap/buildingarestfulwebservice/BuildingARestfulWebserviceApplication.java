package com.ap.buildingarestfulwebservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ap")
public class BuildingARestfulWebserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BuildingARestfulWebserviceApplication.class, args);
	}
}
