import com.pluralsight.service.CustomerService;
import com.pluralsight.service.CustomerServiceImpl;

import javax.swing.*;

public class Main {

    public static void main(String[] args) {
        CustomerService customerService = new CustomerServiceImpl();
        customerService.findAll().forEach(System.out::println);
    }
}
