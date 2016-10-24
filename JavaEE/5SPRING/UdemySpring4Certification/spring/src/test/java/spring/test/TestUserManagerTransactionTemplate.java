package spring.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import spring.jdbc.transactions.orm.UserManagerTransactionTemplate;

@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(locations={"classpath:application-context-jdbc.xml"})
public class TestUserManagerTransactionTemplate {

	@Autowired
	private ApplicationContext context;
	
	@Test
	public void testDoItInTransactionUserManager() {

		UserManagerTransactionTemplate userManagerTransactionTemplate = context.getBean(UserManagerTransactionTemplate.class);	    
		Assert.assertNotNull(userManagerTransactionTemplate);
		userManagerTransactionTemplate.doItInTransactionUserManager();			
		
	}
	
}
