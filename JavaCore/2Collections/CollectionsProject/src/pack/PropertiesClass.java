package pack;

import java.io.IOException;
import java.util.Properties;

public class PropertiesClass {
	public static void main(String[] args) throws IOException {
		System.out.println("Use Properties class");
		
		Properties prop = new Properties();
		
		prop.setProperty("user", "user1");
		prop.setProperty("pass", "Aa123456");
		
		System.out.println(prop.getProperty("pass"));
		System.out.println("===============================");
		java.lang.System.getProperties().save(System.out, "System properties");
		java.lang.System.getProperties().store(System.out, "System properties");

		System.out.println(System.getProperty("user.name"));
	}
}
