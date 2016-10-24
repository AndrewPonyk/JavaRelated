package spring.container.dependency.ioc.XMLconfig;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.util.Assert;

import spring.bean.ComplexBean;
import spring.bean.DatabaseService;
import spring.bean.LoginService;
import spring.bean.MailService;
import spring.bean.RegisterService;
import spring.bean.SuperUser;
import spring.bean.User;
import spring.bean.UserManager;

/*
 • What is an inner bean? Why does it not have a bean id? Can it be reused?
  	No ID is required for inner beans hence it is always anonymous and belongs to parent bean
  	It is not possible to inject or autowire inner beans in other beans 
  	
 • What is a prefix? 
 
 	Prefixes are used in resource strings for convenience
 	Some example are; 
 	classpath: (used for loading file from classpath) classpath:util.properties
 	file: (load as URL from the filesystem) file:///resource/path/file.txt
 	http: (load as URL) http://tunatore.com/resource/path/file.txt 	
 	
 	sample Resource template = ctx.getResource("classpath:util.properties");
    ApplicationContext ctx = new FileSystemXmlApplicationContext("conf/application-context.xml");
    
 */

public class MainApp {

	
	public static void main(String[] args) {
		
		// Creating the application context
		ApplicationContext context = new ClassPathXmlApplicationContext("application-context.xml");	   
	    
		/* XML level profile or Class level @Profile configuration
		((ConfigurableApplicationContext)(context)).getEnvironment().setActiveProfiles("production");
		((ConfigurableApplicationContext)(context)).refresh();
		*/
		
		//no bean id required if the type of requested object is unique
		//hence context does not know the type casting is required with the following approach
		UserManager userManager = (UserManager)context.getBean("userManager");
		
		User user = new User();
		user.setUsername("Tom");
		System.out.println("Previous username:" + user.getUsername());
		
		User userUpdated = userManager.updateUserName(user, "John");
		System.out.println("New username:" + userUpdated.getUsername());

		//no bean id required if the type of requested object is unique
		//no casting is required
	    MailService mailService = context.getBean(MailService.class);	     
	    System.out.println("Inner bean state in mailService: " + mailService.getServiceConfig().getMaxHourlyEmailLimit());
	    boolean result = mailService.sendMessage("You have a new mail");	   
		System.out.println("mail result: " + result);
		
		//Properties file output
		System.out.println("mail.username: " + mailService.getUsername());
		System.out.println("mail.password: " + mailService.getPassword());
		
		//no casting is required with the following approach
	    DatabaseService databaseService = context.getBean("databaseService", DatabaseService.class);	    
		Assert.notNull(databaseService);
		
		LoginService loginService  = context.getBean(LoginService.class);
		loginService.getLogService().log("loginService.log() is called");
				
		RegisterService registerService = context.getBean(RegisterService.class);
		Assert.notNull(registerService);		
		registerService.registerUser(user);
		registerService.getLog().log("log interface log() is called");
		
		try {
			userManager.throwUserUpdateExceptionMethod();			
		} catch (Exception e) {			
		}
		try {
			userManager.throwNotUserUpdateExceptionMethod();	
		} catch (Exception e) {			
		}
		
		userManager.deleteUser(userUpdated);
		
		// Bean inheritance example 
		User testUser = context.getBean("testUser",User.class);
		System.out.println("Bean inheritance testUser username: " + testUser.getUsername());
		System.out.println("Bean inheritance testUser password: " + testUser.getPassword());
		System.out.println("Bean inheritance testUser active: " + testUser.isActive());
		
		SuperUser superUser = context.getBean("superUser",SuperUser.class);
		System.out.println("Bean inheritance superUser systemPassword: " + superUser.getSystemPassword());
		System.out.println("Bean inheritance superUser contact: " + superUser.getContact());
		
		ComplexBean complexBean = context.getBean(ComplexBean.class);
		Assert.notNull(complexBean);		
		System.out.println(complexBean);
		
		//close the application and release all sources and locks
		((ConfigurableApplicationContext)(context)).close();
		
		// or Register a shutdown hook with the JVM runtime, will close the context on JVM shutdown
		//((ConfigurableApplicationContext)(context)).registerShutdownHook();
		
		
	}

}
