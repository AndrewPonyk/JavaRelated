import com.pluralsight.repository.DaoA;
import com.pluralsight.service.ServiceA;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MainWithMyBeans {

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("appContext.xml");
        ServiceA serviceA = applicationContext.getBean("serviceA", ServiceA.class);
        System.out.println(serviceA.getTestString());

        // check Beans scope
        ServiceA serviceA2 = applicationContext.getBean("serviceA", ServiceA.class);
        System.out.println(serviceA + ";" + serviceA2);
    }
}
