package com.pluralsight;

import com.pluralsight.repository.*;
import com.pluralsight.service.CustomerService;
import com.pluralsight.service.CustomerServiceImpl;
import com.pluralsight.service.ServiceA;
import com.pluralsight.service.ServiceAImpl;
import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean(name = "customerService")
    public CustomerService getCustomerService() {
        CustomerServiceImpl customerService = new CustomerServiceImpl(getCuStomerRepository());
        customerService.setCustomerRepository(getCuStomerRepository());;

        return customerService;
    }

    @Bean(name = "customerRepository")
    public CustomerRepository getCuStomerRepository() {
        return new HibernateCustomerRepositoryImpl();
    }

    @Bean(name = "serviceA")
    public ServiceA getServiceA(){
        return new ServiceAImpl();
    }

    @Bean(name = "daoA")
    public DaoA getDaoA(){
        return new DaoAImpl();
    }

    @Bean(name = "daoASecondImplementation")
    public DaoA getDaoASecondImplementation(){
        return new DaoASecondImpl();
    }
}
