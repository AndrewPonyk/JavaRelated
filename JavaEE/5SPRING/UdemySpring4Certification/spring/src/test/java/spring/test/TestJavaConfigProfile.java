package spring.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import spring.bean.DataSource;
import spring.bean.MailService;
import spring.container.dependency.ioc.javaconfig.JavaConfig;

//How do you configure a profile. What are possible use cases where they might be useful? 

@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(classes=JavaConfig.class) //JavaConfig class
@ActiveProfiles(profiles="development")
//@ActiveProfiles(profiles="production") //comment out this one to get production DataSource
public class TestJavaConfigProfile{
	
	@Autowired
    private ApplicationContext context;
    		 
	@Test
	public void tessDataSourceType() {
		
		//We will get TEST Database using profile
		DataSource dataSource = context.getBean(DataSource.class);
		Assert.assertNotNull(dataSource);
		System.out.println("URL DataSource " + dataSource.getUrl());
		System.out.println("Username DataSource " + dataSource.getUsername());
		System.out.println("Password DataSource " + dataSource.getPassword());
		
		//Regular beans in XML Config
		MailService mailService = context.getBean(MailService.class);
		Assert.assertNotNull(mailService);
		mailService.sendMessage("Test Mail Service Email XML Profile");
		
	}

	
}
