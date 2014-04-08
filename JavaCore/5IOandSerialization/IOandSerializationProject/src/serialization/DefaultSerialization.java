package serialization;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class DefaultSerialization {
	final static String FILENAME = System.getProperty("user.dir") + "serializedEmployees.dat";
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		writeObjects();
	
		readObjectsFromFile();
		
	}
	
	public static void writeObjects() throws IOException {
		Employee e1 = new Employee("Andrew", 22, "111");
		Employee e2 = new Employee("Ivan", 44, "222");
		
		ObjectOutputStream oos =
				new ObjectOutputStream(new FileOutputStream(FILENAME));
		
		oos.writeObject(e1);
		oos.writeObject(e2);
		
		oos.close();
	}
	
	public static void readObjectsFromFile() throws IOException, ClassNotFoundException{
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILENAME));
	
		Employee item = null;
		
		try {
			while ( (item = (Employee)ois.readObject()) != null) {
				System.out.println(item.getName() + " " + item.getAge() + " " + item.getPass());
			}
		}catch(EOFException e){
			System.out.println("end of file "); 
			// best solution to find eof file is to write 'count' of objects on the begin of file
		}

		ois.close();
	}
}

class Employee implements java.io.Serializable {
	private String name;
	private int age;
	transient private String pass = "***";

	Employee(String name, int age, String pass) {
		this.name = name;
		this.age = age;
		this.pass = pass;
	}

	String getName() {
		return name;
	}

	int getAge() {
		return age;
	}
	String getPass(){
		return pass;
	}
	
}