package serialization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class CustomSerialization {
	final static String FILENAME = System.getProperty("user.dir")
			+ "serializedEmployees.dat";

	public static void main(String[] args) {

/*		try (ObjectOutputStream oos = new ObjectOutputStream(
				new FileOutputStream("employee.dat"))) {
			SerEmployee se = new SerEmployee("John Doe");
			System.out.println(se);
			oos.writeObject(se);
			System.out.println("se object written to file");
		} catch (Exception e) {
			e.printStackTrace();
		}

		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				"employee.dat"))) {
			Object o = ois.readObject(); // serialization.SerEmployee; no valid constructor
			// because parent object hasnt empty constructor
			System.out.println("se object read from byte array");
		} catch (Exception e) {
			e.printStackTrace();
		}*/

		
		
	}

}

class Employee1 {
	private String name;

	Employee1(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
/*
class SerEmployee extends Employee1 implements Serializable {
	SerEmployee(String name) {
		super(name);
	}
	
	// because parent class hasnt emty constructor we need Custom Serialization
}*/

class SerEmployee implements Serializable
{
	private Employee1 emp;
	private String name;
	SerEmployee(String name){
		this.name = name;
		emp = new Employee1(name);
	}

	private void writeObject(ObjectOutputStream oos) throws IOException{
		oos.writeUTF(name);
	}

	private void readObject(ObjectInputStream ois)throws ClassNotFoundException, IOException{
		name = ois.readUTF();
		emp = new Employee1(name);
	}
	@Override
	public String toString(){
		return name;
	}
}