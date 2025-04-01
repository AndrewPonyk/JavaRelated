package com.optimagrowth.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConfigurationServerApplicationTests {
	@Autowired
	private ApplicationContext applicationContext;
	@Test
	void contextLoads() {
	    assertNotNull(applicationContext, "Application context should not be null");
	    assertTrue(applicationContext.containsBean("configurationServerApplication"), "ConfigurationServerApplication bean should be present");
	}

}
