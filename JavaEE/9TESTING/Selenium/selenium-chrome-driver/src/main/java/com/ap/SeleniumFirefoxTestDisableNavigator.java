package com.ap;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.File;

public class SeleniumFirefoxTestDisableNavigator {

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("webdriver.gecko.driver", "C:\\tmp\\geckodriver.exe");

        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        WebDriver driver = new FirefoxDriver();
        //((JavascriptExecutor)driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        //((JavascriptExecutor)driver).executeScript("location.href='https://www.bet365.com/#/IP/B1'");
        driver.get("https://www.marathonbet.com/en/betting/Table+Tennis+-+382549");
    }

    public static String driver(){
        File driver = new File("C:\\tmp\\chromedriver.exe");
        if(driver.exists()){
            return "C:\\tmp\\chromedriver.exe";
        } else {
            return "/home/andrii/Programs/chromedriver";
        }
    }

}
