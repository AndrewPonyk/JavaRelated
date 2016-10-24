package spring.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import spring.bean.MailService;

//How are you going to create an ApplicationContext in an integration test or a JUnit test? 

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"classpath:application-context.xml"})
public class TestMailServiceApplicationContextAware implements ApplicationContextAware{
	
    private ApplicationContext context;
    
	public void setApplicationContext(ApplicationContext context)
			throws BeansException {
		this.context = context;
		
	}
		
	@Test
	public void testEmail() {
	
		 MailService mailService = context.getBean(MailService.class);
		 Assert.assertNotNull(mailService);
		 mailService.sendMessage("Test Mail Service Email");
		
	}




	
}
