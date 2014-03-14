package pack;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class IterateThroughCollections {
	public static void main(String[] args) {
		System.out.println("iterating through collection");
		
		
		ArrayList<Integer> numb3rs = new ArrayList<>();
		numb3rs.add(10);
		numb3rs.add(10000);
		numb3rs.add(0);

		printElements(numb3rs, System.out);
	}
	
	
	
	public static void printElements(Collection c, PrintStream out) {
		Iterator iterator = c.iterator();
		while ( iterator.hasNext() )
			out.println( iterator.next() );
	}
}
