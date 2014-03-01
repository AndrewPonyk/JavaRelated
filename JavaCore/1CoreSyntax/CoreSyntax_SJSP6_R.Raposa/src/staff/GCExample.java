package staff;

import java.util.Vector;

public class GCExample {
	public static void main(String[] args) throws InterruptedException {
		Vector<Dog> vector = new Vector<Dog>();
		Dog one = new Dog("Snoopy", 4);
		Dog two = new Dog("Lassie", 5);

		vector.add(one);
		vector.add(two);

		one = null;
		System.out.println("Calling gc once...");
		System.gc();

		vector = null;
		System.out.println("Calling gc twice...");
		System.gc();

		two = null;
		System.out.println("Calling gc again...");
		System.gc();
		//Thread.sleep(1);
		System.out.println("End of main...");

	}
}