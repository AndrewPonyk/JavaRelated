import com.pluralsight.service.CustomerService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(Appconfig.class);
        CustomerService customerService = applicationContext.getBean("customerService", CustomerService.class);
        System.out.println(customerService.findAll());
    }
}
