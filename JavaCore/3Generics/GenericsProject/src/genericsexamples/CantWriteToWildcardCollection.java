package genericsexamples;

import java.util.ArrayList;
import java.util.List;

public class CantWriteToWildcardCollection {
	public static void main(String[] args) {
		
		List<? extends Number> numbers = new ArrayList<Integer>();
		
		//numbers.add(Integer.valueOf(100)); //Compiler ERRRRRRRRRRRRRRROR 
		
		List<Number> goodNumbers = (List<Number>) numbers;
		goodNumbers.add(100);
		
		for(Number item : goodNumbers){
			System.out.println(item);
		}
		
		/////////////////////////////
		List<?> wildcards = goodNumbers;
		for(Object item : wildcards){
			wildcards.get(0);// get as Object
			System.out.println(item.getClass()); // Integer
		}
		
		
		/////////////////
		
		List<? super Number > superNumbers = new ArrayList<Object>();
		superNumbers.add(Integer.valueOf(10)); // work
		
	}
}
