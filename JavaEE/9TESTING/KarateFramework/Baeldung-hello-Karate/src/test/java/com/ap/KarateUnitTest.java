package com.ap;

import com.intuit.karate.junit4.Karate;
import cucumber.api.CucumberOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@CucumberOptions(features = "classpath:karate")
public class KarateUnitTest {

    @Test
    public void test1(){

    }
}
