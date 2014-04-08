package writersandreaders;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

public class FileWriterHello {
	private static final String PROJECT_FILES = System.getProperty("user.dir") + "/files/";
	
	public static void main(String[] args) throws IOException {
		
		FileWriter fw = null;
		FileReader fr = null;
		try {
			fw = new FileWriter(PROJECT_FILES + "temp.txt");

			
			fw.write("............. end");
			
			fr = new FileReader(PROJECT_FILES + "temp.txt");
			// using FileWriter we can write to file char[] 
			
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			try {
				fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
