package randomaccessfile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

public class RandomAccessFileHello {
	private static final String FILENAME = System.getProperty("user.dir")
			+ "/files/employee.dat";

	public static void main(String[] args) {
		//simplyReadWriteToRandomAccessFile();
		//changeintValueInRandomAccessFile();
		changeStringInRandomAccessFile();
	}

	public static void simplyReadWriteToRandomAccessFile() {
		try {
			// Start <SIMPLY WRITE READ>
			RandomAccessFile rnf = new RandomAccessFile(FILENAME, "rw");
			ArrayList<Dto> values = new ArrayList<>();

			// we will write Strings to file in such way : 
			// - maximum string length equals 100
			// - if string length is less then 100 , we will add spaces.
			Dto val1 = new Dto(10, String.format("%100s", "hello10"));
			Dto val2 = new Dto(100,String.format("%100s", "hello100") );
			Dto val3 = new Dto(1000, String.format("%100s", "hello1000......"));
			values.add(val1);
			values.add(val2);
			values.add(val3);

			for (Dto item : values) {
				rnf.writeInt(item.i);
				rnf.writeUTF(item.s);
			}
			;

			rnf.close();

			rnf = new RandomAccessFile(FILENAME, "rw");
			while (rnf.getFilePointer() < rnf.length() - 2) { // i dont know why 2 =) , but if works =)
				System.out.println(rnf.readInt() + "  " + rnf.readUTF().trim());
			}
			// ////////////////////// FINISH <SIMPLY WRITE READ>

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	
	public static void changeintValueInRandomAccessFile(){
		simplyReadWriteToRandomAccessFile();
		System.out.println("=================================");
		try {
			RandomAccessFile rnf = new RandomAccessFile(FILENAME, "rw");
			
			rnf.seek(0);
			rnf.writeInt(101);
			
			rnf.seek(0);
			while (rnf.getFilePointer() < rnf.length() - 2) { // i dont know why 2 =) , but if works =)
				System.out.println(rnf.readInt() + "  " + rnf.readUTF());
			}
			
			rnf.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}
	
	public static void changeStringInRandomAccessFile(){
		System.out.println("Write data to file employee.dat");
		simplyReadWriteToRandomAccessFile();
		System.out.println("=================================");
		try {
			RandomAccessFile rnf = new RandomAccessFile(FILENAME, "rw");
			rnf.seek(4);
			
			rnf.writeUTF(String.format("%100s", "New Line"));      // replace !!! text inside file =)
			
			rnf.seek(0);
			while (rnf.getFilePointer() < rnf.length() - 2) { // i dont know why 2 =) , but if works =)
				System.out.println(rnf.readInt() + "  " + rnf.readUTF().trim());
			}
			
			rnf.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}

class Dto {
	public Dto(int i, String s) {
		this.i = i;
		this.s = s;
	}
	public int i;
	public String s;

}