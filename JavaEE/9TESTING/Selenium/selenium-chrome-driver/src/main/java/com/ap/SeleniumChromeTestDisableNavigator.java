package com.ap;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.util.ArrayList;

public class SeleniumChromeTestDisableNavigator {
    public static void main(String[] args) throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", driver());

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--disable-blink-features");
        chromeOptions.addArguments("--disable-blink-features=AutomationControlled");

        WebDriver driver = new ChromeDriver(chromeOptions);
        Thread.sleep(3000);
//        driver.get("https://www.bet365.com/#/IP/B1");
        String baseUrl = "http://www.google.co.uk/";
        driver.get(baseUrl);
        driver.findElement(By.cssSelector("body")).sendKeys(Keys.CONTROL +"t");

        ArrayList<String> tabs = new ArrayList<String> (driver.getWindowHandles());
        driver.switchTo().window(tabs.get(1)); //switches to new tab
        driver.get("https://www.facebook.com");
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
