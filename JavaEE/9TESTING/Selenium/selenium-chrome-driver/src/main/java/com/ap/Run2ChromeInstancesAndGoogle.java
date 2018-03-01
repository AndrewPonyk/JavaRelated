package com.ap;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.File;

public class Run2ChromeInstancesAndGoogle {
    // run 2 chrome instances
    public static void main(String[] args) throws InterruptedException {

        System.setProperty("webdriver.chrome.driver", driver());

        new Thread(() -> {
            try {
                WebDriver driver1 = new ChromeDriver();
                driver1.get("http://www.google.com/xhtml");

                WebElement searchBox1 = driver1.findElement(By.name("q"));
                searchBox1.sendKeys("ChromeDriver 999");
                searchBox1.submit();
                Thread.sleep(5000);  // Let the user actually see something!
                driver1.quit();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        WebDriver driver = new ChromeDriver();
        driver.get("http://www.google.com/xhtml");
        WebElement searchBox = driver.findElement(By.name("q"));
        searchBox.sendKeys("ChromeDriver");
        searchBox.submit();
        Thread.sleep(5000);  // Let the user actually see something!
        driver.quit();
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
