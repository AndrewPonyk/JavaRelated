import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import com.pluralsight.repository.CustomerRepository;
import com.pluralsight.repository.HibernateCustomerReporistoryImp;
import com.pluralsight.service.CustomerService;
import com.pluralsight.service.CustomerServiceImpl;


@Configuration
@ComponentScan("com.pluralsight")
@PropertySource("app.properties")
public class AppConfig {

	@Bean
	public static PropertySourcesPlaceholderConfigurer getPropertySourcesPlaceholderConfigurer(){
		return new PropertySourcesPlaceholderConfigurer();
	}
	
	@Bean(name = "customerService")
	@Scope("singleton")
	public CustomerService getCustomerService(){
		//CustomerServiceImpl customerService = new CustomerServiceImpl();
		//customerService.setCustomerRepository(getCustomerRepository());
		
		// Constructor injection will be : 
		CustomerServiceImpl customerService = new CustomerServiceImpl(getCustomerRepository());
		// but dont forget to define default constructors !!!
		
		
		// !!!!
		// If you dont wont using setters or constructor injection you can use @Autowired, and spring will inject it
		// You will use @ComponentScan, @Service and @Autowired annotations
		
		return customerService;
	}
	
	@Bean(name = "customerRepository")
	public CustomerRepository getCustomerRepository(){
		return new HibernateCustomerReporistoryImp();
	}
}
