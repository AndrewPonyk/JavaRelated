package com.ap.services;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ServiceTest {

    @Autowired
    Service service;

    @Test
    public void testContextAndBeanLoad(){
        Assert.assertEquals("This is service message for UNIT tests", service.getMessage());
    }
}
