package org.example2;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Scanner;

public class PrintAllDataTypes {
	public static void main(String[] args) {
		// Primitive data types
		byte b = 127;
		short s = 32767;
		int i = 2147483647;
		long l = 9223372036854775807L;
		float f = 3.14f;
		double d = 3.14159265359;
		char c = 'A';
		boolean bool = true;

		System.out.println("Primitive Data Types:");
		System.out.println("byte: " + b);
		System.out.println("short: " + s);
		System.out.println("int: " + i);
		System.out.println("long: " + l);
		System.out.println("float: " + f);
		System.out.println("double: " + d);
		System.out.println("char: " + c);
		System.out.println("boolean: " + bool);

		// String and String manipulation
		String greeting = "Hello, World!";
		System.out.println("\nString Example:");
		System.out.println("Original String: " + greeting);
		System.out.println("Uppercase: " + greeting.toUpperCase());
		System.out.println("Substring (7-12): " + greeting.substring(7, 12));

		// ArrayList
		ArrayList<String> fruits = new ArrayList<>();
		fruits.add("Apple");
		fruits.add("Banana");
		fruits.add("Cherry");
		System.out.println("\nArrayList Example:");
		System.out.println("Fruits: " + fruits);

		// HashMap
		HashMap<String, Integer> ageMap = new HashMap<>();
		ageMap.put("Alice", 25);
		ageMap.put("Bob", 30);
		ageMap.put("Charlie", 35);
		System.out.println("\nHashMap Example:");
		System.out.println("Ages: " + ageMap);

		// Date
		Date currentDate = new Date();
		System.out.println("\nDate Example:");
		System.out.println("Current Date and Time: " + currentDate);

		// Scanner (for user input)
		Scanner scanner = new Scanner(System.in);
		System.out.println("\nScanner Example:");
		System.out.print("Enter your name: ");
		String name = scanner.nextLine();
		System.out.println("Hello, " + name + "!");
		scanner.close();
	}
}
