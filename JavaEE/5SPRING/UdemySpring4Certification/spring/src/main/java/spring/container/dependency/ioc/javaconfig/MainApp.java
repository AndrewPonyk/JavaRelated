package spring.container.dependency.ioc.javaconfig;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.util.Assert;

import spring.bean.ComplexBean;
import spring.bean.DatabaseService;
import spring.bean.LoginService;
import spring.bean.MailService;
import spring.bean.RegisterService;
import spring.bean.User;
import spring.bean.UserManager;

public class MainApp {

	public static void main(String[] args) {
		
		ApplicationContext  context = new AnnotationConfigApplicationContext(JavaConfig.class);		   
	    
		//Autowired
		UserManager userManager = context.getBean(UserManager.class);
		User user = new User();
		user.setUsername("Tom");
		System.out.println("Previous username:" + user.getUsername());
		User userUpdated = userManager.updateUserName(user, "John");		
		System.out.println("New username:" + userUpdated.getUsername());
			
		//Using Bean
	    MailService mailService = context.getBean(MailService.class);
	    //MailService mailService = (MailService)context.getBean("mailService"); //default bean ID same as method name
	    boolean result = mailService.sendMessage("You have a new mail");	   
		System.out.println("mail result: " + result);
		
		//Properties file output
		System.out.println("mail.username: " + mailService.getUsername());
		System.out.println("mail.password: " + mailService.getPassword());
		
		//Autowired through constructor
	    DatabaseService databaseService = context.getBean(DatabaseService.class);	    
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
		
		ComplexBean complexBean = context.getBean(ComplexBean.class);
		Assert.notNull(complexBean);		
		System.out.println(complexBean);
		
		((ConfigurableApplicationContext)(context)).close();
	
		
	}

}
