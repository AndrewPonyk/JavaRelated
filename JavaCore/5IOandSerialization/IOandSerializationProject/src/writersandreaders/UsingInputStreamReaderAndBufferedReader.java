package writersandreaders;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class UsingInputStreamReaderAndBufferedReader {
	public static void main(String[] args) throws IOException {
		InputStreamReader r = 
				new InputStreamReader(new FileInputStream(System.getProperty("user.dir") + "/files/1.txt"));
		
		BufferedReader buf = new BufferedReader(r);
		
		String line;
		while( (line = buf.readLine() ) != null) {
			System.out.println(line);			
		}
	
		
		OutputStreamWriter w = 
				new OutputStreamWriter(new FileOutputStream(System.getProperty("user.dir") + "/files/1.txt.copy"));
		BufferedWriter bufwriter = new BufferedWriter(w);
		
		bufwriter.write("hello");
		
		
		bufwriter.close(); // dont forget to close ! all streams and readers
		w.close();
		buf.close();
		r.close();
	}
}
