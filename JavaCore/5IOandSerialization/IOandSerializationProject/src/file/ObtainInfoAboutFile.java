package file;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ObtainInfoAboutFile {
	public static void main(String[] args) throws IOException {
		String filename = args.length > 0 ? args[0] : System.getProperty("user.dir");
		
		File file = new File(filename);
		
		System.out.println("Exists = " + file.exists());
		System.out.println("Absolute path = "+file.getAbsolutePath());
		System.out.println("Canonical path = "+file.getCanonicalPath());
		System.out.println("Name = "+file.getName());
		System.out.println("Parent = "+file.getAbsoluteFile().getParent()); // Get parent , when file hasnt absolute path !
		System.out.println("Path = "+file.getPath());
		System.out.println("Is absolute = "+file.isAbsolute());
		System.out.println("Is folder = " + file.isDirectory());
		
		
	}
}