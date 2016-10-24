package spring.test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import spring.jdbc.transactions.orm.DBLog;
import spring.jdbc.transactions.orm.DBLogService;
import spring.jdbc.transactions.orm.User;
import spring.jdbc.transactions.orm.UserManager;

@RunWith(SpringJUnit4ClassRunner.class)  
@ContextConfiguration(locations={"classpath:application-context-jdbc.xml"})
public class TestJDBC {
	
	@Autowired
	private ApplicationContext context;
	
	@Test
	@Transactional
	@Rollback(value=false)
	public void testDBLogServiceLogMethod() {

		DBLogService dbLogService = context.getBean(DBLogService.class);	    
		Assert.assertNotNull(dbLogService);
		dbLogService.log("1 MainAppJDBCTransactionsORM TEST LOG INSERT SRTING IN LOG TABLE");
		dbLogService.log("2 MainAppJDBCTransactionsORM TEST LOG INSERT SRTING IN LOG TABLE");
		dbLogService.log("3 MainAppJDBCTransactionsORM TEST LOG INSERT SRTING IN LOG TABLE");
		dbLogService.log("4 MainAppJDBCTransactionsORM TEST LOG INSERT SRTING IN LOG TABLE");
		
		List<DBLog> dbLogs = dbLogService.queryAllDBLogs();
		for (Iterator<DBLog> iterator = dbLogs.iterator(); iterator.hasNext();) {
			DBLog dbLog = iterator.next();
			System.out.println("IDLOG: " + dbLog.getIDLOG());
			System.out.println("LOGSTRING: " + dbLog.getLOGSTRING());			
		}
		
	}
	
	@Test
	public void testUserManagerQueryUserWithInternalRowMapper()  {
	
		UserManager userManager = context.getBean(UserManager.class);	    
		Assert.assertNotNull(userManager);
		User u = userManager.queryUserWithInternalRowMapper(2);
		Assert.assertNotNull(u);
		System.out.println("testUserManagerQueryUserWithInternalRowMapper: " + u.getUsername());
	}
	
	@Test
	public void testUserManagerUpdateUserName() {
	
		UserManager userManager = context.getBean(UserManager.class);	    
		Assert.assertNotNull(userManager);
		User u = new User();
		u.setUsername("test@outlook.com");
		userManager.updateUserName(u, "testspring@outlook.com");
		
	}
	
	@Test
	@Rollback(value=false)
	public void testUserManagerAddUSER() {
	
		UserManager userManager = context.getBean(UserManager.class);	    
		Assert.assertNotNull(userManager);
		User user = new User();
		user.setUsername("testuser12345@outlook.com");
		user.setPassword("12345");
		user.setActive(true);
		userManager.addUSER(user);
		
	}
	
	@Test
	public void testUserManagerCountAllUsers() {
	
		UserManager userManager = context.getBean(UserManager.class);	    
		Assert.assertNotNull(userManager);
		System.out.println(userManager.countAllUsers());
		
	}
	
	@Test
	public void testUserManagerLogAllUserInfo() {
	
		UserManager userManager = context.getBean(UserManager.class);			
		Assert.assertNotNull(userManager);
		userManager.logAllUserInfo();
		
	}
	
	@Test
	public void testUserManagerQueryUserWithResultSetExtractor() {
	
		UserManager userManager = context.getBean(UserManager.class);	    
		Assert.assertNotNull(userManager);
		List<User> testUsers =  userManager.queryUserWithResultSetExtractor();
		for (User u : testUsers) {
			System.out.println("Test users");
			System.out.println("testUserManagerQueryUserWithResultSetExtractor : " + u.getUsername());
			
		}
		
	}
	
	@Test
	public void testQueryForListOfUsers() {
		System.out.println("testQueryForListOfUsers");
		UserManager userManager = context.getBean(UserManager.class);			
		Assert.assertNotNull(userManager);
		List<Map<String,Object>> users = userManager.queryForListOfUsers();
		System.out.println(users);
		for (Map<String,Object> row : users) {
			System.out.println("MAP VALUE: " + row.get("IDUSER"));
			System.out.println("MAP VALUE: " + row.get("USERNAME"));			
		}		
	}

	@Test
	public void testQueryForMapUser() {
		System.out.println("testQueryForMapUser");
		UserManager userManager = context.getBean(UserManager.class);			
		Assert.assertNotNull(userManager);
		Map<String, Object> user = userManager.queryForMapUser("2");
		System.out.println(user);
		System.out.println("MAP VALUE: " + user.get("IDUSER"));
		System.out.println("MAP VALUE: " + user.get("USERNAME"));			
		
	}
}
