package pack;

import java.util.ArrayList;

public class ConvertCollectionToArray {
	public static void main(String[] args) {
		System.out.println("Convert ArrayList to array");
		
		ArrayList<String> strings = new ArrayList<>();
		
		strings.add("1");
		strings.add("2");
		strings.add("3");
		
		
		strings.remove("2");
		
		String [] mas = strings.toArray(new String[0]);
		
		for(String item:mas){
			System.out.println(item);
		}
	}
}
