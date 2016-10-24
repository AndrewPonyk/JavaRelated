package spring.test;


import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.springframework.transaction.annotation.Transactional;

import spring.persistence.User;
import spring.persistence.UserManager;

@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(locations={"classpath:application-context-persistence.xml"})
@Transactional
public class TestJPAPersistence{
	
	@Autowired
    private ApplicationContext context;
    		 
	@Before
	public void setUp() throws Exception {
		new EmbeddedDatabaseBuilder().build();
	}
	
	@Test
	public void testQueryfindAllUsersJPA() {
		
		System.out.println("testQueryfindAllUsersJPA is called");
		UserManager userManager =  context.getBean(UserManager.class);
		Assert.assertNotNull(userManager);
		List<User> users = userManager.queryfindAllUsersJPA();
		Assert.assertNotNull(users);
		for (User user : users) {
			System.out.println("IDUSER : " + user.getIdUser());
			System.out.println("USERNAME : " + user.getUsername());
			System.out.println("PASSWORD : " + user.getPassword());
			System.out.println("ACTIVE : " + user.isActive());			
		}	
		
	}

	@Test
	public void testQueryCountTAllUsersJPA() {
		
		System.out.println("testQueryCountTAllUsersJPA is called");
		UserManager userManager =  context.getBean(UserManager.class);
		Assert.assertNotNull(userManager);
		int count = userManager.queryCountAllUsersJPA();
		System.out.println("Users count: " + count);
	
	}
	
	@Test
	public void testFindByIdUser() {
		
		System.out.println("testFindByIdUSer is called");
		UserManager userManager =  context.getBean(UserManager.class);
		Assert.assertNotNull(userManager);
		User user = userManager.queryFindByIdUser(2);
		System.out.println("User: " + user.getUsername());
	}
	
	@Test
	public void testInsertUserByIdUser() {
		System.out.println("testInsertUserByIdUser is called");
		UserManager userManager =  context.getBean(UserManager.class);
		Assert.assertNotNull(userManager);
		userManager.insertUserByIdUser("testInsert@outlook.com", "123456", true);
		
		List<User> users = userManager.queryfindAllUsersJPA();
		Assert.assertNotNull(users);
		
		for (User user : users) {
			System.out.println("IDUSER : " + user.getIdUser());
			System.out.println("USERNAME : " + user.getUsername());
			System.out.println("PASSWORD : " + user.getPassword());
			System.out.println("ACTIVE : " + user.isActive());			
		}	
	}
	
	@Test
	public void testDeleteUserByIdUser() {
		System.out.println("testDeleteUserByIdUser is called");
		UserManager userManager =  context.getBean(UserManager.class);
		Assert.assertNotNull(userManager);
		userManager.deleteUserByIdUser(2);
		List<User> users = userManager.queryfindAllUsersJPA();
		Assert.assertNotNull(users);
		
		for (User user : users) {
			System.out.println("IDUSER : " + user.getIdUser());
			System.out.println("USERNAME : " + user.getUsername());
			System.out.println("PASSWORD : " + user.getPassword());
			System.out.println("ACTIVE : " + user.isActive());			
		}	
	}
				
	
}
