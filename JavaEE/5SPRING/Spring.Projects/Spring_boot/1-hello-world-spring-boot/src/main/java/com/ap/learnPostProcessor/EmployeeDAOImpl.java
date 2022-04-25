package com.ap.learnPostProcessor;

import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Repository
public class EmployeeDAOImpl {
    public Object createNewEmployee() {
        return "test";
    }

    @PostConstruct
    public void initBean() {
        System.err.println("Init Bean for : EmployeeDAOImpl");
    }

    @PreDestroy
    public void destroyBean() {
        System.err.println("Destroy Bean for : EmployeeDAOImpl");
    }
}
