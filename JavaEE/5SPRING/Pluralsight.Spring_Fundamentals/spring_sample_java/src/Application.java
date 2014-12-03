import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.pluralsight.service.CustomerService;
import com.pluralsight.service.CustomerServiceImpl;


public class Application {
	
	public static void main(String[] args) {
		
		
		//pain point 2 : when you see  '<Interface> i = new <Interface>Impl' - we should use SPRING )))
		//CustomerService service = new CustomerServiceImpl();
		
		ApplicationContext appContext = new AnnotationConfigApplicationContext(AppConfig.class);
		
		CustomerService customerService = appContext.getBean("customerService", CustomerService.class);
		
		
		System.out.println(customerService.findAll().get(0).getFirstName());
	}
	
}