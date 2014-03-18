package genericmethodsexample;

import java.util.ArrayList;

public class JavaDoesntAllowArraysOfGenericObjects {
	public static void main(String[] args) {
		
		// ArrayList<String> [] lists = new ArrayList<String>[10]; COMPILER ERROR
		
		
		// not type safe , but work =)
		ArrayList<String> [] lists = new ArrayList[10]; 
		lists[0] = new ArrayList<String>();
		lists[0].add("hello ");
		lists[0].add("world");
		
		for(String item:lists[0]){
			System.out.print(item);
		}
		
		
	}
}
