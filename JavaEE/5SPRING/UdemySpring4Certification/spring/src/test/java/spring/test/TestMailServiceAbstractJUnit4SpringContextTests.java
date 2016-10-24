package spring.test;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import spring.bean.MailService;

//How are you going to create an ApplicationContext in an integration test or a JUnit test? 

@ContextConfiguration(locations={"classpath:application-context.xml"})
public class TestMailServiceAbstractJUnit4SpringContextTests extends AbstractJUnit4SpringContextTests{

	@Test
	public void testEmail() {
		MailService mailService = applicationContext.getBean(MailService.class);
		Assert.assertNotNull(mailService);
		mailService.sendMessage("Test Mail Service Email");
		
	}

	
}
