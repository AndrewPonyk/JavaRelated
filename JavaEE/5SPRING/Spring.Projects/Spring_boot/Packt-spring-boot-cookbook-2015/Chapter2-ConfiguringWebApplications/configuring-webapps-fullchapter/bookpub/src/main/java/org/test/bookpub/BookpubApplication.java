package org.test.bookpub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookpubApplication {
		public static void main(String[] args) {
		SpringApplication.run(BookpubApplication.class, args);
	}

	@Bean
	public StartupRunner startupRunner(){
		return new StartupRunner();
	}
}
