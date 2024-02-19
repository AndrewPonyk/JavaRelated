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
		
		//print in reverse order
		for(int i=lists[0].size()-1;i>=0;i--){
			System.out.print(lists[0].get(i));
		}

		//print in random order
		for(int i=0;i<lists[0].size();i++){
			System.out.print(lists[0].get(i));
		}
		
	}
}
