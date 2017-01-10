/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.ap;

import javax.ejb.embeddable.EJBContainer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author andrii
 */
public class HelloBeanTest {
    
    public HelloBeanTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of sayHello method, of class HelloBean.
     */
    @Test
    public void testSayHello() throws Exception {
        System.out.println("sayHello");
        String name = "user";
        EJBContainer container = javax.ejb.embeddable.EJBContainer.createEJBContainer();
        HelloBean instance = (HelloBean)container.getContext().lookup("java:global/classes/HelloBean");
        String expResult = "Hello, user";
        String result = instance.sayHello(name);
        assertEquals(expResult, result);
        container.close();
    }
    
}
