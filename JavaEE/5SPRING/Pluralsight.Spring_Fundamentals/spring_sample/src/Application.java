import com.pluralsight.service.CustomerService;
import com.pluralsight.service.CustomerServiceImpl;


public class Application {
	
	public static void main(String[] args) {
		
		
		//pain point 2 : when you see  '<Interface> i = new <Interface>Impl' - we should use SPRING )))
		CustomerService service = new CustomerServiceImpl();
		
		System.out.println(service.findAll().get(0).getFirstName());
	}
	
}