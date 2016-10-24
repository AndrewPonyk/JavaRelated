package spring.container.dependency.ioc.javaconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

import spring.bean.DataSource;
import spring.bean.MailService;
import spring.util.ComplexBeanFactoryBeanImplementation;

//Questions answered in Core Spring 4.x Study Guide 
/*
   • Are beans lazily or eagerly instantiated by default? How do you alter this behavior? 
		By default Spring framework creates singleton beans eagerly but by setting lazy-init attribute beans can be initialized lazily
		sample <bean id="mailService" class="spring.bean.MailService" lazy-init="true"/> 
		or @Lazy annotation is used to lazily init beans

    • How many profiles can you have? 
		You can have many of them as long as you add @Profile to your any @Component or @Configuration or configure it application-context.xml

	• How does the @Qualifier annotation complement the use of @Autowired?
 		@Qualifier annotation allows to inject different objects to AutoWired classes through its defined custom naming 
	
 */


@Configuration //let the Spring Framework knows this class is used as Configuration like it is done in application-context.xml file
@ComponentScan(basePackages= {"spring.bean","spring.aspect"}) //components under packages will be scanned automatically with Spring Framework
//@ComponentScan(basePackages= {"spring.bean","other.packages"})
@PropertySource("classpath:util.properties") 
//This is used to point util.properties file
//xml version is <context:property-placeholder location="classpath:util.properties"/>  
@EnableAspectJAutoProxy
public class JavaConfig {
	
	@Bean
	//@Lazy
	@Scope("singleton") // Scopes are singleton (default), prototype, request, session and global-session
	public MailService mailService() { //default bean id is mailService
		MailService mailService = new MailService();
		mailService.setMailServer("smtp.gmail.com");
		mailService.setProtocol("smtp");
		mailService.setPort("9999");
		return mailService;
	}

	//Can you use @Bean together with @Profile? 

	/*@Bean	
	@Profile("development")
	@Scope("singleton") 
	public DataSource dataSourceDevelopment() { //default bean id is dataSourceDevelopment
		DataSource dataSource = new DataSource();
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://127.0.0.1/test");
		dataSource.setUsername( "username");
		dataSource.setPassword("password");
		return dataSource;
	}
	*/
	
	@Bean	
	//@Profile("production")
	@Scope("singleton") 
	public DataSource dataSource() { //default bean id is dataSource
		DataSource dataSource = new DataSource();
		dataSource.setDriverClassName("com.mysql.jdbc.Driver");
		dataSource.setUrl("jdbc:mysql://127.0.0.1/");
		dataSource.setUsername("production username");
		dataSource.setPassword("production password");
		return dataSource;
	}
	
	//FactoryBean implementation
    @Bean
    public ComplexBeanFactoryBeanImplementation complexBean(){
    	return new ComplexBeanFactoryBeanImplementation();
    }
	
	//This bean is used for configuring PropertySourcesPlaceholderConfigurer
	/* XML sample equivalent 
	 * <context:property-placeholder location="classpath:util.properties" />	 	 
	 */
	@Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }
	

}
