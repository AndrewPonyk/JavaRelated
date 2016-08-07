package com.my;

import org.junit.AfterClass;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.junit.Assert.*;

public class ThisIsIntegrationTest {

	WebDriver driver = new FirefoxDriver();
	WebDriverWait wait = new WebDriverWait(driver, 10);

	@AfterClass
	public static void tearDown(){
	}

	@Test
	public void itIsIntegrationTest() throws InterruptedException{
		driver.get("http://localhost:8080/using-cargo-containers");
		WebElement element = wait.until(ExpectedConditions.elementToBeClickable(By.id("hello")));
		assertEquals("Hello World!", element.getText());
	}
}
