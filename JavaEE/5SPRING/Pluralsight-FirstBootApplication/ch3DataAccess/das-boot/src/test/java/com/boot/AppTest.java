package com.boot;

import com.boot.controller.HomeController;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    @Test
    public void testApp(){
        HomeController hc = new HomeController();
        String result = hc.home();
        Assert.assertEquals("Das Boot, reporting for duty!", result);
    }
}
