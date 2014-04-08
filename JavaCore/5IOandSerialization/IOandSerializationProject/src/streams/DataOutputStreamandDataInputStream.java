package streams;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class DataOutputStreamandDataInputStream {
	public static void main(String[] args) throws IOException {
		File file = new File(System.getProperty("user.dir") + "/files/int_double_string_int_double_string.txt");
		Bean [] beans = {new Bean(1, 1.0, "hello 1"), new Bean(2, 2.0, "hello 2"), new Bean(3, 3.0, "hello 3")};
		
		DataOutputStream dos = new DataOutputStream(new FileOutputStream(file));
		
		
		for (int i = 0; i < beans.length; i++) {
			dos.writeInt(beans[i].getI());
			dos.writeDouble(beans[i].getD());
			dos.writeUTF(beans[i].getS());
			
		}
		dos.close();
		
		
		DataInputStream dis = new DataInputStream(new FileInputStream(file));
		ArrayList<Bean> beansList = new ArrayList<>();
		
		while (dis.available() > 0) {
			beansList.add(new Bean(dis.readInt(), dis.readDouble(), dis.readUTF()));			
		}
		dis.close();
		
		for(Bean item : beansList){
			System.out.println(item);
		}
		
	}
}

class Bean {
	private int i;
	private double d;
	private String s;

	public Bean(int i, double d, String s) {
		this.i = i;
		this.d = d;
		this.s = s;
	}
	
	@Override
	public String toString() {
		return "{" + this.getI() + " " + this.getD() + " " + this.getS() + "}";
	}
	
	public int getI() {
		return i;
	}

	public double getD() {
		return d;
	}

	public String getS() {
		return s;
	}
}
