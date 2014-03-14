package pack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;



public class CollectionsBinarySearch {
	public static void main(String[] args) {
		System.out.println("Binary search");
		
		 List < String > names = Arrays.asList("Tom", "Dick",
				"Harry", "Sue");
				 Collections.sort(names);
				 int x = Collections.binarySearch(names, "Tom");
				 System.out.println(x);
	}
}
