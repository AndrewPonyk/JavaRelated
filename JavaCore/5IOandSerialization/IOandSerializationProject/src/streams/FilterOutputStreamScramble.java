package streams;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;
import java.util.Scanner;

// Scramble - shyfruvaty

public class FilterOutputStreamScramble extends FilterOutputStream{
	private int [] map;
	
	public FilterOutputStreamScramble(OutputStream out, int[] map) {
		super(out);
		if (map == null)
			throw new NullPointerException("map is null");
		if (map.length != 256)
			throw new IllegalArgumentException("map.length != 256");
		
		this.map = map;
	}
	
	@Override
	public void write(int b) throws IOException {
		//out.write(b + 3);
		out.write(map[b]); // scramble based on map , map is the key =)
	}
	
	public static void main(String[] args) {
		System.err.println("Using FilterOutputStream");
		Scanner scan = new Scanner(System.in);
		String filename = "";
		int [] keysMap = createRandomMap();
		
		System.out.println("Input filename ");
		filename = System.getProperty("user.dir") +"/files/"+ scan.nextLine();
		
		// Start Scrambling
		try (FileInputStream fis = new FileInputStream(filename); FilterOutputStreamScramble sos =
				new FilterOutputStreamScramble(new FileOutputStream(filename+".scrambled"), keysMap)){
			int b = 0;
			while ((b = fis.read()) != -1)
				sos.write(b);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// End Scrambling
		
		//Start  Unscrabling
		try (UnscrambleInputStream fis = new UnscrambleInputStream(new FileInputStream(filename+".scrambled"), keysMap);
				FileOutputStream fos = new FileOutputStream(filename + ".scrambled" + ".unscrambled");){
			int b = 0;
			
			while ( (b = fis.read()) != -1) {
				fos.write(b);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		//Finish Unscrabling
	}
	
	public static int[] createRandomMap() {
		int[] map = new int[256];
		for (int i = 0; i < map.length; i++)
			map[i] = i;
		
		// shuffle
		Random r = new Random(0);
		for (int i = 0; i < map.length; i++) {
			int n = r.nextInt(map.length);
			int temp = map[i];
			map[i] = map[n];
			map[n] = temp;
		}
		return map;
	}
}

class UnscrambleInputStream extends FilterInputStream {
	private int[] reversedMap;

	UnscrambleInputStream(InputStream in, int[] map) {
		super(in);
		if (map == null)
			throw new NullPointerException("map is null");
		if (map.length != 256)
			throw new IllegalArgumentException("map.length != 256");
		this.reversedMap = new int[256];
		for(int i=0;i<256;i++){
			this.reversedMap[map[i]] = i ;
		}
	}
	
	@Override
	public int read() throws IOException {
		int value = in.read();
		if(value == -1 ){
			return value;
		}	
		return this.reversedMap[value];
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int nBytes = in.read(b, off, len);
		if (nBytes <= 0)
			return nBytes;
		for (int i = 0; i < nBytes; i++)
			b[off + i] = (byte) reversedMap[off + i];
		return nBytes;
	}
}