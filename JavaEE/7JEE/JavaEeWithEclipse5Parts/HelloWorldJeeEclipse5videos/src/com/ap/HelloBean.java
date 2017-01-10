package com.ap;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;

/**
 * Session Bean implementation class HelloBean
 */
@Stateless
@LocalBean
public class HelloBean {

    /**
     * Default constructor. 
     */
    public HelloBean() {

    }

    public String sayHello(String name){
    	return "Hello, " + name + "!";   
    }
}
