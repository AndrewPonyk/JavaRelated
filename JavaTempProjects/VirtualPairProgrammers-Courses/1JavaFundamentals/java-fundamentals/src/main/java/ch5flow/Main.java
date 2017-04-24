package ch5flow;

import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		System.out.println("Flow");
		int[] j = { 11, 22, 33, 44 };
		
		// --------- for
		for (int i = 0; i < j.length; i++) {
			System.out.println("Value at position:" + i + ", is: " + j[i]);
		}
		
		// --------- while
		// ...

		// switch
		// note: only constants( final or literals) can be  in case statement
		Integer i = new Scanner(System.in).nextInt();

		switch (i){
			case 1:
				System.out.println("I=1");
				break;
			case 10:
				System.out.println("I=10");
				break;
			default:
				System.out.println("Default val");
		}

		// Java 8 switch changes
		// 1) Allow String in switch
		// 2) Allow wrappers in switch
		//Character, Byte, Short, and Integer , !!! NOT Long

	}
}