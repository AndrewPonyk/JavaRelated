package hello.app;

import com.ap.services.Service;
import com.ap.services.ServiceConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@Import(ServiceConfiguration.class)
@RestController
public class DemoApplication {

    @RequestMapping("/")
    public String home(){
        return service.getMessage();
    }

    @Autowired
    Service service;

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
