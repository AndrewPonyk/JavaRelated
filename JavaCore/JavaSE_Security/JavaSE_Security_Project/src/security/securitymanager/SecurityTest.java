package security.securitymanager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class SecurityTest {
	private final static String projectPath = System.getProperty("user.dir");
	
	public static void main(String[] args) throws FileNotFoundException {
		// Is there a SecurityManger registered?
		System.out.println("SecurityManager: " + System.getSecurityManager());

		// Checking if we can open a file for reading
		FileInputStream fis = new FileInputStream(projectPath + "/test.txt");
		System.out.println("File successfully opened");

		// Checking if we can access a vm property
		System.out.println(System.getProperty("file.encoding"));
	}
}
