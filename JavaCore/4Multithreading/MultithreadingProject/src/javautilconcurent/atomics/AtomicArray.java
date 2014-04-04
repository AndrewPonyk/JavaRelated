package javautilconcurent.atomics;

import java.util.concurrent.atomic.AtomicIntegerArray;

public class AtomicArray {
	public static void main(String[] args) {
		AtomicIntegerArray vector = new AtomicIntegerArray(10);
		
		
		vector.incrementAndGet(5);
		
		for (int i = 0; i < vector.length(); i++) {
			System.out.println(vector.get(i));
		}
		
		
	}
}
