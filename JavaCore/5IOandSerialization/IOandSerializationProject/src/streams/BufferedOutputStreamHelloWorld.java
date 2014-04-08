package streams;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class BufferedOutputStreamHelloWorld {
	public static void main(String[] args) throws IOException {
		Scanner scan = new Scanner(System.in);
		String filename = scan.nextLine();
		
		FileOutputStream fos = new FileOutputStream(System.getProperty("user.dir") + "/files/" + filename );
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		
		String text = "Hello";
		
		for (int i = 0; i < text.length(); i++) {
			bos.write(text.charAt(i));
		}
		
		bos.close();
		fos.close();
		
	}

}
