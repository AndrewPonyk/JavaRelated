package spring.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import spring.bean.MailService;

//How are you going to create an ApplicationContext in an integration test or a JUnit test? 

@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(locations={"classpath:application-context.xml"})
public class TestMailService /*implements ApplicationContextAware*/ {
	
	@Autowired
	private ApplicationContext context;

	/*public void setApplicationContext(ApplicationContext context)
			throws BeansException {
		mailService = context.getBean(MailService.class);
		Assert.assertNotNull(mailService);
	}
	*/
	 
	@Test
	public void testEmail() {
		MailService mailService = context.getBean(MailService.class);
		Assert.assertNotNull(mailService);
		mailService.sendMessage("Test Mail Service Email");
		
	}

	
}
