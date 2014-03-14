package pack;

import java.util.TreeSet;

public class TreeSetExample {
	public static void main(String[] args) {
		TreeSet<String> names = new TreeSet<>();
		
		names.add("Yurii");
		names.add("James");
		names.add("Andrew");
		
		
		
		for(String item :names){
			System.out.println(item);
		}
	}
}
