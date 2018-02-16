package com.ap;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class Run2ChromeInstancesAndGoogle {
    // run 2 chrome instances
    public static void main(String[] args) throws InterruptedException {

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

        System.setProperty("webdriver.chrome.driver", "C:\\tmp\\chromedriver.exe");

        WebDriver driver = new ChromeDriver();
        driver.get("http://www.google.com/xhtml");
        WebElement searchBox = driver.findElement(By.name("q"));
        searchBox.sendKeys("ChromeDriver");
        searchBox.submit();
        Thread.sleep(5000);  // Let the user actually see something!
        driver.quit();
    }
}
