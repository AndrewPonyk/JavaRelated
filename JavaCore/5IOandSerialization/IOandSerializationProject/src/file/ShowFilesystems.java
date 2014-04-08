package file;

import java.io.File;
import java.io.IOException;

public class ShowFilesystems {
	public static void main(String[] args) throws IOException {
		// displaying filesystems on you machine
		//When I run this application on my Windows XP platform, I receive
		//the following output, which
		//reveals four available roots:
		//A:\
		//C:\
		//D:\
		//E:\
		//If I ran DumpRoots on a Unix or Linux platform, I would
		//receive one output line consisting of the
		//virtual filesystem root (/).
		 
		for (File item : File.listRoots() ){
			System.out.println(item);
		}
		
		//The java.io package’s classes default to resolving relative
		//pathnames against the current user (also
		//known as working) directory, which is identified by system property
		//user.dir, and which is typically the directory
		//in which the JVM was launched
		File userDir = new File(System.getProperty("user.dir"));
		
		System.out.println(userDir.getAbsolutePath()); // project folder (for eclipse)
		System.out.println(userDir.getParent());	// PARENT folder, useful thing
		
		
		
		/*The conversion of a pathname string to or from an abstract pathname is inherently platformdependent.
		When a pathname string is converted to an abstract pathname, the names within it are
		separated by the default name-separator character or by any other name-separator character that is
		supported by the underlying platform. For example, File(String pathname) converts pathname string
		/x/y to abstract pathname /x/y on a Unix or Linux platform, and this same pathname string to abstract
		pathname \x\y on a Windows platform.*/
		
		
		// So use '/x/y/filename'  - it will works in Windows and in Linux

		
		/*The default name-separator character is obtainable from system property file.separator, and is also
		stored in File’s separator and separatorChar class fields. The first field stores the character in a
		java.lang.String instance and the second field stores it as a char value. Neither name of these final fields
		follows the convention of appearing entirely in uppercase.*/
	}
}












